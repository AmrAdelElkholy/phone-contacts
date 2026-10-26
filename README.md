# Easy Phone

<img alt="Logo" src="graphics/easy-phone-logo.png" width="120" />

Easy Phone is a dialer and contacts manager in one app. It works as your default
phone app and also handles viewing, editing and organizing your contacts.

## Features

- Dialer with call history, favorites, speed dial and a full in-call screen
- Contact details, editing, groups, and vCard import/export with automatic backups
- WhatsApp and WhatsApp Business shortcuts for any number in the call log, the
  dialpad and the contact details screen
- Call blocking, including hidden and unknown numbers
- No ads and no unnecessary permissions

## Building

The CI workflow in `.github/workflows/build-apk.yml` builds a signed APK
(`assembleFossRelease`) and a Play Store bundle (`bundleGplayRelease`).
Locally, run:

```
./gradlew assembleFossRelease
```

Release signing reads `keystore.properties` (see `keystore.properties_sample`)
or the `SIGNING_*` environment variables.

## Credits

Easy Phone is a fork of open-source apps by [Fossify](https://github.com/FossifyOrg):

- [Fossify Phone](https://github.com/FossifyOrg/Phone) (the `app` module)
- [Fossify Contacts](https://github.com/FossifyOrg/Contacts) (the `contacts` module)
- [Fossify Commons](https://github.com/FossifyOrg/commons) (the `commons` module)

Many thanks to the Fossify team and contributors. See [NOTICE.md](NOTICE.md).

## License

Licensed under the [GNU General Public License v3.0](LICENSE), like the
projects it is based on.
