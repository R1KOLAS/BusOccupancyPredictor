Put detached backend data files here if you want to run the backend outside the current monorepo.

Expected files:
- routes_2gis.json
- stops_2gis.json

When these files are not present here, the backend will try to read them from:
- ../app/src/main/assets/
- ../
