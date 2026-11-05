# Third-party notices

## BlackBox Reborn

Clony vendors the `Bcore`, `black-reflection`, and `compiler` modules from
[fdavids77/BlackBoxReborn](https://github.com/fdavids77/BlackBoxReborn) in
`third_party/BlackBoxReborn/`. The included upstream README describes the
project as GPL-3.0, while the included upstream `LICENSE` file contains the
Apache License 2.0 text. Both original files are retained. At the user's
direction, Clony distributes the combined project under GPL-3.0 and preserves
the upstream notices and Apache terms for the vendored source.

Clony modifications are documented in
[`third_party/BlackBoxReborn/CLONY_MODIFICATIONS.md`](third_party/BlackBoxReborn/CLONY_MODIFICATIONS.md).
The vendored Android manifest was narrowed to runtime networking and service
permissions, and internal proxy components were made non-exported where the
system does not need to bind them. Play Integrity interception was removed.

## Other dependencies

The vendored annotation processor uses AutoService and JavaPoet. Their
respective artifacts carry their own license and notice terms; consult the
resolved dependency metadata before redistributing a release build.
