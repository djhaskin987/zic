Roadmap for 1.0
===============

Story: Init database
--------------------

- The `init` command works
- All the stuff in `io.clj` works except filelock and file-root-path

Story: Install Package
----------------------

- The `install` command works
- All the stuff in `io.clj` works, including filelock and file-root-path
- Functions:

  - `zic.fs/download`
  - `zic.fs/unpack`
  - `zic.db/add-pkg`
  - `zic.db/serialize-pkg`
  - `zic.package/install-package`
  - `zic.package/install-packages`
  - `zic.cli/install`

Story: Info on Package
----------------------

- The `info` command works
- Functions:
  - `zic.cli/info`
  - `zic.db/lookup-pkg`
  - `zic.db/deserialize-pkg`

Story: Files of a Package
-------------------------

- The `files` command works
- Functions:
  - `zic.cli/files`

Story: Verify a Package
-----------------------

- A column in the files section for sha256sum
- Functions:
  - `zic.fs/hash-files` and its use in install-packages
  - `zic.cli/verify`

Story: Install package next to existing packages
------------------------------------------------

- Functions:
  - `zic.db/file-owned?`
  - `zic.db/list-archive-files`
  - `zic.package/packages-conflict?`
  - `zic.package/package-installable?`
  

Story: Install a Package that Already Exists
--------------------------------------------

  - `zic.db/pkg-exists?`
  - `zic.db/lookup-pkg-id`
  - `zic.db/erase-pkg`
  - `zic.fs/rm-files`
  - `zic.db/list-files`
  - `zic.package/remove-without-cascade` and its use in install-package

Story: Remove Package(s)
------------------------

- The `remove` command works
- Functions needed that aren't already done:
  - `zic.cli/remove`
  - `zic.package/remove-cascade`
  - `zic.db/used-by`

Story: List Orphans
-------------------

- The `orphans` command works
- An `is_source` column in the package table
- Functions:
  - `zic.package/graph-sources` and its use in install-packages
  - `zic.cli/orphans`
  - `zic.db/non-source-pkgs`
  - `zic.db/used-somewhere?`
