#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=789583178f764f4e0e5599b423eb36d1f1114717
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/b7156eab5677/spigot-api.jar?ref=$ref" > "libs/spigot-api.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/3bc6fa2477eb/BungeeCord.jar?ref=$ref" > "libs/BungeeCord.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/0116d714822b/ItemsAdder_3.5.0-r2.jar?ref=$ref" > "libs/ItemsAdder_3.5.0-r2.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/086e3971ea63/api-5.4.jar?ref=$ref" > "libs/api-5.4.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/c84700df5942/MMOItems-6.10.jar?ref=$ref" > "libs/MMOItems-6.10.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/660ff2a6ec86/MythicLib-1.7.jar?ref=$ref" > "libs/MythicLib-1.7.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/db5beb70f316/gunsandgadgets-1.0.6.jar?ref=$ref" > "libs/gunsandgadgets-1.0.6.jar"
sha256sum --check .github/dependencies.sha256
