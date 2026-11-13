# Contributing to VideoDL

Thanks for considering a contribution. Keep changes focused, testable and aligned with the multi-platform application scope.

## Development setup

Python engine (Python 3.12+ and FFmpeg):

~~~bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
~~~

C SDK (C11, CMake and libcurl):

~~~bash
sudo apt-get install -y build-essential cmake pkg-config libcurl4-openssl-dev
~~~

C++ client additionally requires nlohmann-json.

## Before opening a pull request

~~~bash
make test
make test-c
make build-cpp
~~~

For Android, open clients/android in Android Studio or run:

~~~bash
gradle -p clients/android assembleDebug
~~~

## Project conventions

- Put HTTP contract code in videodl/api.py; do not embed route logic in the yt-dlp adapter.
- Put yt-dlp calls in videodl/engine.py.
- Keep URL validation, quality selection, task state and request limiting in their own modules.
- Keep the server-side API backward-compatible unless a documented version change is intended.
- Add tests for new task transitions, API responses, input boundaries and error paths.
- Never commit cookies, access tokens, passwords, private URLs or generated media.
- Do not add a language solely to increase the language count. It must own a real module and have CI coverage.
- Do not claim DRM bypass, account-gated access or universal site support.

## Pull request checklist

- [ ] The change has one clear responsibility.
- [ ] Tests were added or updated.
- [ ] Python tests pass.
- [ ] Relevant native/client build passes.
- [ ] Docs and API contract are updated.
- [ ] No secrets or user data are included.
