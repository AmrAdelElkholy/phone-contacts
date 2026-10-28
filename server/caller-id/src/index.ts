// Caller ID server for Easy Phone, see docs/caller-id/PLAN.md (phase 2).
//
// GET  /v1/delta?since=<version>&after=<number>  rows changed after a version, oldest first
// GET  /v1/status                                current version and number count
// POST /v1/admin/numbers                         add, change or delete numbers (bearer token)
//
// Phones never send their own numbers or contacts, they only download changes.

const PAGE_SIZE = 1000;
const MAX_ADMIN_ROWS = 1000;
const NUMBER_KEY = /^\d{1,3}:\d{2,15}$/;
const CATEGORIES = new Set([
	"emergency", "bank", "telecom", "government", "hospital", "delivery", "airline", "company",
	"pharmacy", "restaurant", "hotel", "education", "shop", "place",
]);

interface AdminRow {
	number: string;
	name_en?: string;
	name_ar?: string;
	category?: string;
	source?: string;
	deleted?: boolean;
}

type DeltaRow = [number: string, nameEn: string, nameAr: string, category: string, deleted: number, version: number];

export default {
	async fetch(request, env): Promise<Response> {
		const url = new URL(request.url);
		try {
			if (request.method === "GET" && url.pathname === "/v1/delta") {
				return await getDelta(url, env);
			}
			if (request.method === "GET" && url.pathname === "/v1/status") {
				return await getStatus(env);
			}
			if (request.method === "POST" && url.pathname === "/v1/admin/numbers") {
				return await updateNumbers(request, env);
			}
			return json({ error: "not found" }, 404);
		} catch (error) {
			console.error(JSON.stringify({ message: "request failed", path: url.pathname, error: String(error) }));
			return json({ error: "internal error" }, 500);
		}
	},
} satisfies ExportedHandler<Env>;

async function getDelta(url: URL, env: Env): Promise<Response> {
	const since = Number.parseInt(url.searchParams.get("since") ?? "0", 10);
	const afterParam = url.searchParams.get("after");
	if (!Number.isSafeInteger(since) || since < 0 || (afterParam !== null && !NUMBER_KEY.test(afterParam))) {
		return json({ error: "bad cursor" }, 400);
	}
	// without a number the whole "since" version is already known, "~" sorts after every key
	const after = afterParam ?? "~";

	// (version, number) is the cursor, rows sharing a version can span pages
	const results = await env.DB.prepare(
		`SELECT number, name_en, name_ar, category, deleted, version FROM numbers
		 WHERE version > ?1 OR (version = ?1 AND number > ?2)
		 ORDER BY version, number LIMIT ?3`,
	)
		.bind(since, after, PAGE_SIZE + 1)
		.raw<DeltaRow>();

	const more = results.length > PAGE_SIZE;
	const rows = more ? results.slice(0, PAGE_SIZE) : results;
	// every phone on the same version asks for the same page, let caches share it for a while
	return json({ rows, more }, 200, "public, max-age=3600");
}

async function getStatus(env: Env): Promise<Response> {
	const row = await env.DB.prepare("SELECT max(version) AS version, count(*) AS numbers FROM numbers WHERE deleted = 0")
		.first<{ version: number | null; numbers: number }>();
	return json({ version: row?.version ?? 0, numbers: row?.numbers ?? 0 }, 200, "public, max-age=300");
}

async function updateNumbers(request: Request, env: Env): Promise<Response> {
	if (!(await isAdmin(request, env))) {
		return json({ error: "unauthorized" }, 401);
	}

	let body: { rows?: AdminRow[] };
	try {
		body = await request.json();
	} catch {
		return json({ error: "invalid json" }, 400);
	}

	const rows = body.rows;
	if (!Array.isArray(rows) || rows.length === 0 || rows.length > MAX_ADMIN_ROWS) {
		return json({ error: `send 1 to ${MAX_ADMIN_ROWS} rows` }, 400);
	}

	const invalid = rows.findIndex((row) => !isValidRow(row));
	if (invalid !== -1) {
		return json({ error: `row ${invalid} is invalid` }, 400);
	}

	// one new version for the whole batch, always above the current one
	const current = await env.DB.prepare("SELECT coalesce(max(version), 0) AS version FROM numbers").first<{ version: number }>();
	const version = Math.max(Date.now(), (current?.version ?? 0) + 1);

	const statement = env.DB.prepare(
		`INSERT INTO numbers (number, name_en, name_ar, category, source, deleted, version)
		 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
		 ON CONFLICT (number) DO UPDATE SET name_en = ?2, name_ar = ?3, category = ?4, source = ?5, deleted = ?6, version = ?7`,
	);
	await env.DB.batch(
		rows.map((row) =>
			statement.bind(
				row.number,
				(row.name_en ?? "").trim(),
				(row.name_ar ?? "").trim(),
				row.category ?? "place",
				row.source ?? "admin",
				row.deleted ? 1 : 0,
				version,
			),
		),
	);

	console.log(JSON.stringify({ message: "numbers updated", count: rows.length, version }));
	return json({ updated: rows.length, version });
}

function isValidRow(row: AdminRow): boolean {
	if (typeof row !== "object" || row === null || typeof row.number !== "string" || !NUMBER_KEY.test(row.number)) {
		return false;
	}
	if (row.deleted) {
		return true;
	}
	const nameEn = row.name_en ?? "";
	const nameAr = row.name_ar ?? "";
	return (
		typeof nameEn === "string" && typeof nameAr === "string" &&
		(nameEn.trim() !== "" || nameAr.trim() !== "") &&
		nameEn.length <= 200 && nameAr.length <= 200 &&
		(row.category === undefined || CATEGORIES.has(row.category)) &&
		(row.source === undefined || (typeof row.source === "string" && row.source.length <= 50))
	);
}

async function isAdmin(request: Request, env: Env): Promise<boolean> {
	const header = request.headers.get("Authorization") ?? "";
	if (!header.startsWith("Bearer ") || !env.ADMIN_TOKEN) {
		return false;
	}

	// hash both sides so the comparison takes the same time whatever the input length
	const encoder = new TextEncoder();
	const [provided, expected] = await Promise.all([
		crypto.subtle.digest("SHA-256", encoder.encode(header.slice("Bearer ".length))),
		crypto.subtle.digest("SHA-256", encoder.encode(env.ADMIN_TOKEN)),
	]);
	return crypto.subtle.timingSafeEqual(provided, expected);
}

function json(body: unknown, status = 200, cacheControl = "no-store"): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": cacheControl },
	});
}
