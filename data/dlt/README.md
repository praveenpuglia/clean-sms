# DLT sender header registry

`jio-headers.csv`: every SMS header registered on Jio's DLT platform with its principal entity
(the registered company) and purpose. Snapshot **as on 8 Oct 2026**.

- Source: [Jio TrueConnect → List of PEs and headers](https://trueconnect.jio.com/#/listPeHeader)
  (public PDF, no login). Regenerate with `scripts/dlt/extract_jio_headers.py <pdf>`.
- **Headers are case-sensitive**: `INDIGO`/`IndiGo` belong to InterGlobe Aviation, `Indigo` to
  Indigo Paints. Never normalize case when matching.
- **Look-alike headers exist**: a header that reads like a brand may be registered to someone else.
  Brand matching must go through the registered principal entity (see `data/brands.json`).
- `extra-headers.csv`: rows copied from Vi/BSNL/MTNL's larger shared list (~952k headers, public PDF at
  https://www.vilpower.in/header_link_doc/, as on 9 Oct 2026) for curated brands missing from Jio's
  list. Only the rows we need are kept; the generator fails if one ever disagrees with Jio's owner.
