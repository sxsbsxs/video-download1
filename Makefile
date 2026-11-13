PYTHON ?= python3
CMAKE ?= cmake
BUILD_DIR ?= build

.PHONY: test test-c build-c build-cpp build-android clean

test:
	$(PYTHON) -m compileall -q app.py videodl tests
	$(PYTHON) -m unittest discover -s tests -v

build-c:
	$(CMAKE) -S native/c-client -B $(BUILD_DIR)/c-client -DCMAKE_BUILD_TYPE=Release
	$(CMAKE) --build $(BUILD_DIR)/c-client --parallel

test-c: build-c
	ctest --test-dir $(BUILD_DIR)/c-client --output-on-failure

build-cpp:
	$(CMAKE) -S clients/cpp -B $(BUILD_DIR)/cpp -DCMAKE_BUILD_TYPE=Release
	$(CMAKE) --build $(BUILD_DIR)/cpp --parallel

build-android:
	gradle -p clients/android assembleDebug

clean:
	rm -rf $(BUILD_DIR) clients/android/.gradle clients/android/build
