rootProject.name = "jungsan_app"

pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Kotlin 2.4 지원 최소9.1.29 이상의 9.1 안정 패치. AGP 전체 대신 Android 최적화 도구만 교체한다.
            // 버전 카탈로그 생성보다 먼저 플러그인에 로드해야 하므로 이 버전은 settings에서 지정한다.
            classpath("com.android.tools:r8:9.1.57")
        }
    }
    repositories {
        google {
            // 구글 저장소는 안드로이드 관련 그룹만 뒤진다 — 전부 뒤지면 해석이 느려진다.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// 화면·로직을 한 모듈에 담는다. 지금 규모에서 더 쪼개면
// 얻는 것보다 빌드 설정을 관리하는 비용이 크다 — 필요해지면 그때 나눈다.
include(":composeApp")
