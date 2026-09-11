rootProject.name = "jungsan_app"

pluginManagement {
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
