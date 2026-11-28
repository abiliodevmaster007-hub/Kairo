# Kairo Android APK Packaging Shell

Módulo independente (Gradle Composite Build + AGP 8.8.0 + CMake C++17) para gerar o APK Android sem precisar do Android Studio.

## Pré-requisitos CLI
- **JDK 17**
- **Android SDK** (`ANDROID_HOME` ou `ANDROID_SDK_ROOT`) com `platforms;android-35` e `build-tools;35.0.0`
- **Android NDK r27c (`27.2.12479018`) + CMake `3.22.1`** para compilar `libkairo_render.so`

## Execução via Kairo CLI
Na raiz `kairo/`:
```bash
gradle :tools:kairo-cli:run --args="apk --dir ."
```

Saída gerada:
- `build/outputs/apk/debug/kairo-android-apk-debug.apk`
