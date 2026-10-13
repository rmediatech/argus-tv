# ==============================================================================
# 📱 Argus — Android Build & Deploy Automation Makefile
# ==============================================================================

export ANDROID_HOME ?= $(HOME)/Android/Sdk
export JAVA_HOME ?= /usr/lib/jvm/java-1.21.0-openjdk-amd64

APK_PATH := app/build/outputs/apk/debug/app-debug.apk
PACKAGE_NAME := com.rmediatech.sovereignremote

DEVICE ?= $(shell adb devices | grep -E "device$$" | grep -v ":5555" | head -n 1 | awk '{print $$1}')
ADB_CMD := adb $(if $(DEVICE),-s $(DEVICE),)

.PHONY: all build release install run clean devices logs help

all: build

help:
	@echo "Argus Sovereign Remote Build & Deployment Targets:"
	@echo "  make build        - Compile debug APK using Gradle"
	@echo "  make release      - Compile signed release AAB bundle and APK"
	@echo "  make release-sign - Full build, sign, and GPG provenance seal (SOVEREIGN-SIGN-01)"
	@echo "  make install      - Push and install debug APK to connected Android device"
	@echo "  make run          - Launch Argus on connected Android device"
	@echo "  make logs         - Stream device logcat logs"
	@echo "  make devices      - List connected ADB devices (USB & Wi-Fi)"
	@echo "  make clean        - Clean build caches and outputs"

build:
	JAVA_HOME=$(JAVA_HOME) ./gradlew assembleDebug

release:
	JAVA_HOME=$(JAVA_HOME) ./gradlew bundleRelease assembleRelease

release-sign: release
	@echo "🔐 Assembling and signing sovereign release distribution (SOVEREIGN-SIGN-01)..."
	@mkdir -p dist/release-v1.0.0
	@cp app/build/outputs/bundle/release/app-release.aab dist/release-v1.0.0/argus-v1.0.0.aab
	@cp app/build/outputs/apk/release/app-release.apk dist/release-v1.0.0/argus-v1.0.0.apk
	@cd dist/release-v1.0.0 && sha256sum argus-v1.0.0.aab argus-v1.0.0.apk > SHA256SUMS
	@if gpg --list-keys 4E428019A109578B >/dev/null 2>&1; then \
		gpg --batch --yes --armor --detach-sign --default-key 4E428019A109578B --output dist/release-v1.0.0/SHA256SUMS.asc dist/release-v1.0.0/SHA256SUMS; \
		echo "✅ Sovereign detached signature verified:"; \
		gpg --verify dist/release-v1.0.0/SHA256SUMS.asc dist/release-v1.0.0/SHA256SUMS; \
	fi
	@echo "🎉 Release ready in dist/release-v1.0.0/"
	@command -v sovereign-tone >/dev/null 2>&1 && sovereign-tone bottle || true

install:
	@if [ ! -f $(APK_PATH) ]; then \
		echo "APK not found. Building first..."; \
		JAVA_HOME=$(JAVA_HOME) ./gradlew assembleDebug; \
	fi
	@echo "Installing to device: $(DEVICE)"
	$(ADB_CMD) install -r $(APK_PATH)

run: install
	@echo "Launching on device: $(DEVICE)"
	$(ADB_CMD) shell am start -n $(PACKAGE_NAME)/.MainActivity

devices:
	adb devices -l

logs:
	$(ADB_CMD) logcat -v color | grep -E "($(PACKAGE_NAME)|Roku|SSDP)"

clean:
	JAVA_HOME=$(JAVA_HOME) ./gradlew clean
