# DLT sender header registry

`jio-headers.csv`: every SMS header registered on Jio's DLT platform with its principal entity
(the registered company) and purpose. Snapshot **as on 8 Oct 2026**.

- Source: [Jio TrueConnect → List of PEs and headers](https://trueconnect.jio.com/#/listPeHeader)
  (public PDF, no login). Regenerate with `scripts/dlt/extract_jio_headers.py <pdf>`.
- **Headers are case-sensitive**: `INDIGO`/`IndiGo` belong to InterGlobe Aviation, `Indigo` to
  Indigo Paints. Never normalize case when matching.
- **Look-alike headers exist**: a header that reads like a brand may be registered to someone else.
  Brand matching must go through the registered principal entity (see `data/brands.json`).
- Vi/BSNL/MTNL publish a larger shared list (~952k headers) at https://www.vilpower.in/header_link_doc/
  if a brand is missing here.
