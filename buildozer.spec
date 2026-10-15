[app]

title = NetCheck
package.name = netcheck
package.domain = org.netcheck

source.dir = .
source.include_exts = py,png,jpg,kv,atlas
source.exclude_dirs = .github,.git,bin,.buildozer

version = 0.1

requirements = python3,kivy==2.3.0,requests,urllib3,certifi,idna,charset-normalizer

orientation = portrait
fullscreen = 0

# Права: доступ в интернет
android.permissions = INTERNET,ACCESS_NETWORK_STATE

android.api = 33
android.minapi = 24
android.archs = arm64-v8a, armeabi-v7a
android.accept_sdk_license = True
android.allow_backup = True

[buildozer]
log_level = 1
warn_on_root = 1
