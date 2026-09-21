# Golden files

Real requests and responses of the Jev API, read by the tests of **both** modules
([ADR-0009](../docs/adr/0009-two-native-modules-no-shared-code.md)). They are what keeps the
Scala 3 module and the Scala 2.13 module in step, since the two share no code.

Empty until M2, which records the first real responses. Never write a file here by hand: a
golden test on invented JSON proves little. Never store an API key in one.
