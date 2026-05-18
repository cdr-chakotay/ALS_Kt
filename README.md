# ALS_Kt – A Kotlin Library for Apples Location Services

[![CI](https://github.com/cdr-chakotay/ALS_Kt/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/cdr-chakotay/ALS_Kt/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.kufl/als_kt.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.kufl/als_kt)
[![Javadoc](https://javadoc.io/badge2/org.kufl/als_kt/javadoc.svg)](https://javadoc.io/doc/org.kufl/als_kt)

## Installation

```kotlin
implementation("org.kufl:als_kt:0.1.0")
```

- Maven Central: <https://central.sonatype.com/artifact/org.kufl/als_kt>
- API docs: <https://javadoc.io/doc/org.kufl/als_kt>

ALS_Kt is a Kotlin library that provides a simple and efficient way to interact with Apple's Location Services (ALS) using kotlin.
It allows developers to query cell tower information and retrieve location data based on cell identifiers.
The main purpose of this library is integration into Android applications.
For convenience, a CLI fat JAR is also provided to query ALS directly from the command line.

## Features

- Query ALS database for cell information based on cell identifiers
  - Radio Access Technology (RAT): (GSM (2G), WCDMA (3G, UMTS), TD_SCDMA (3G, Mainly China), LTE (4G), NR (5G));
  - MCC (Mobile Country Code)
  - MNC (Mobile Network Code)
  - Area: LAC (Location Area Code) for 2G, 3G / TAC (Tracking Area Code) for 4G, 5G
  - Cell ID
- Retrieve location data associated with specific cell towers
- Unknown cells are returned as a single entry with a null location and can be detected via `ALSQueryCell.isValid()`
- Coroutine support via `requestCellsAsync`
- Easy integration with Kotlin projects

### Request and Response

Each request to the ALS database must specify the cell tower lookup parameters listed above: RAT, MCC, MNC, area (LAC/TAC) and cell ID.
The library returns the response as a list of `ALSQueryCell` objects, which has at least one entry.
The first entry (index `0`) corresponds to the cell that was queried.
Any further entries relate to nearby cells reported by ALS alongside the result.

Use `ALSQueryCell.isValid()` to decide how to interpret the response:

- If the first cell is valid, the requested cell is part of ALS's dataset and its `location` approximates the cell tower's position.
- If the first cell entry is not valid, the queried cell is unknown to ALS and the entire list should be discarded.

`ALSQueryCell.isValid()` returns `true` only when the cell has a non-negative cell ID and its `location` itself is valid and `accuracyMeters > 0`.

Each `ALSQueryCell` exposes the following properties:

- `technology`: The radio access technology of the cell (`GSM`, `WCDMA`, `TD_SCDMA`, `LTE`, `NR`).
- `country` / `network` / `area` / `cellId`: The identifiers echoed back from the request.
- `physicalCell`: Physical cell identifier (PCI/PSC). Typically only populated for LTE and 5G, optional.
- `frequency`: Frequency channel number (ARFCN/UARFCN/EARFCN/NR-ARFCN). Typically only populated for LTE and 5G, optional.
- `location`: An `ALSQueryLocation` describing the estimated tower position, with:
  - `latitude` / `longitude`: Decimal degrees.
  - `accuracyMeters`: Estimated accuracy radius around the cell tower position, in meters.
  - `reachMeters`: Estimated reception range of the cell tower, in meters.
  - `score`: Internal ALS quality score for this data point, where 1000 is the maximum value.

Neighboring cells returned within the result list can be cached to avoid frequent requests.
This should only be done if the first result is valid, since ALS sometimes returned arbitrary data for invalid results.
Also, `ALSQueryCell.isValid()` should be called on each entry in result list prior to caching as validity of additional results is not guaranteed.

### Note on the WCDMA return type

WCDMA cells share the GSM entity within the ALS database.
As a result, they are internally queried through the GSM entity by this library.
To distinguish between WCDMA and GSM cells, the following heuristic applies:

- GSM cells have a 2-octet (16-bit) cell ID. ([3GPP TS 23.003](https://www.3gpp.org/dynareport/23003))
- WCDMA / UMTS cells have a 28-bit cell ID. ([3GPP TS 23.003](https://www.3gpp.org/dynareport/23003))
- ALS GSM responses are classified as WCDMA when the cell ID exceeds 16 bits. This may lead to WCDMA cells being incorrectly classified as GSM in cases where the [WCDMA RNC ID = 0](https://github.com/mozilla/ichnaea/issues/373). There is no clear evidence whether this occurs in practice, but it is noted here for completeness.

## Getting Started

After cloning, run the following once to generate the protobuf sources (required before the IDE can resolve imports):

```shell
./gradlew generateProto
```

## Building

### Library JAR

Builds `build/libs/als_kt.jar` — the library without bundled dependencies, intended for integration into other projects.

```shell
./gradlew jar
```

### CLI Fat JAR

Builds `build/libs/als_kt-cli.jar` — a self-contained executable JAR with all dependencies bundled.

```shell
./gradlew cliJar
```

## Usage Example with the CLI Fat JAR in the dev environment

```shell
./gradlew cliJar
java -jar build/libs/als_kt-cli.jar --tech LTE --mcc 262 --mnc 1 --area 1492 --cell 27025922 --timeoutMs 5000
```

## TODO

Currently, not all features are fully implemented. The following tasks are planned for future development:

- [ ] Implement WiFi lookups.

## Copyright Notice

Copyright Florian Künzig - 2026

ALS_Kt incorporates work from [GrapheneOS NetworkLocation](https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation) [(MIT License)](https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation/blob/16-qpr2/LICENSE).

This library is released under the MIT License.

## Acknowledgements

Special thanks to [Lukas Arnold](https://lukasarnold.de/) for his help with debugging the UMTS lookup and for providing valuable feedback and insights. He also maintains his own ALS Python library in the [BaseTrace repository](https://github.com/seemoo-lab/BaseTrace).
