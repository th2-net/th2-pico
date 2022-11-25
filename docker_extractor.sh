#!/bin/sh
#
# This script pulls and extracts all files from an image in Docker Hub.
#
# Copyright (c) 2020-2022, Jeremy Lin
#
# Permission is hereby granted, free of charge, to any person obtaining a
# copy of this software and associated documentation files (the "Software"),
# to deal in the Software without restriction, including without limitation
# the rights to use, copy, modify, merge, publish, distribute, sublicense,
# and/or sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.

PLATFORM_DEFAULT="linux/amd64"
PLATFORM="${PLATFORM_DEFAULT}"
OUT_DIR="./output"
NUMBER_OF_LAYERS=1
TOKEN="token"
REPOSITORY_TYPE="ghcr"

usage() {
    echo "This script pulls and extracts all files from an image in Docker Hub."
    echo
    echo "$0 [OPTIONS...] IMAGE[:REF]"
    echo
    echo "IMAGE can be a community user image (like 'some-user/some-image') or a"
    echo "Docker official image (like 'hello-world', which contains no '/')."
    echo
    echo "REF is either a tag name or a full SHA-256 image digest (with a 'sha256:' prefix)."
    echo "The default ref is the 'latest' tag."
    echo
    echo "Options:"
    echo
    echo "  -p PLATFORM         Pull image for the specified platform (default: ${PLATFORM})"
    echo "                      For a given image on Docker Hub, the 'Tags' tab lists the"
    echo "                      platforms supported for that image."
    echo "  -o OUT_DIR          Extract image to the specified output dir (default: ${OUT_DIR})"
    echo "  -n NUMBER_OF_LAYERS number of layers to be extracted, will be taken in reverse order"
    echo "  -t REPOSITORY_TYPE  type of repo from which extraction take place. Valid values: ghcr, nexus"
    echo "  -h                  Show help with usage examples"
}

usage_detailed() {
    usage
    echo
    echo "Examples:"
    echo
    echo "# Pull and extract all files in the 'hello-world' image tagged 'latest'."
    echo "\$ $0 hello-world:latest"
    echo
    echo "# Same as above; ref defaults to the 'latest' tag."
    echo "\$ $0 hello-world"
    echo
    echo "# Pull the 'hello-world' image for the 'linux/arm64/v8' platform."
    echo "\$ $0 -p linux/arm64/v8 hello-world"
    echo
    echo "# Pull an image by digest."
    echo "\$ $0 hello-world:sha256:90659bf80b44ce6be8234e6ff90a1ac34acbeb826903b02cfa0da11c82cbc042"
}

if [ $# -eq 0 ]; then
    usage_detailed
    exit 0
fi

while getopts ':ho:p:n:t:' opt; do
    case $opt in
        t)
            REPOSITORY_TYPE=${OPTARG}
            ;;
        n)
            NUMBER_OF_LAYERS=${OPTARG}
            ;;
        o)
            OUT_DIR="${OPTARG}"
            ;;
        p)
            PLATFORM="${OPTARG}"
            ;;
        h)
            usage_detailed
            exit 0
            ;;
        \?)
            echo "ERROR: Invalid option '-$OPTARG'."
            echo
            usage
            exit 1
            ;;
        \:) echo "ERROR: Argument required for option '-$OPTARG'."
            echo
            usage
            exit 1
            ;;
    esac
done
shift $(($OPTIND - 1))

if [ $# -eq 0 ]; then
    echo "ERROR: Image to pull must be specified."
    echo
    usage
    exit 1
fi

have_curl() {
    command -v curl >/dev/null
}

have_wget() {
    command -v wget >/dev/null
}

if ! have_curl && ! have_wget; then
    echo "This script requires either curl or wget."
    exit 1
fi

image_spec="$1"
image=$(echo $image_spec|  rev | cut -d':' -f2- | rev)
ref=$(echo $image_spec| rev | cut -d':' -f-1 | rev)
echo "image and ref"
echo $image
echo $ref

# Split platform (OS/arch/variant) into separate variables.
# A platform specifier doesn't always include the `variant` component.
OLD_IFS="${IFS}"
IFS=/ read -r OS ARCH VARIANT <<EOF
${PLATFORM}
EOF
IFS="${OLD_IFS}"

# Given a JSON input on stdin, extract the string value associated with the
# specified key. This avoids an extra dependency on a tool like `jq`.
extract() {
    local key="$1"
    # Extract "<key>":"<val>" (assumes key/val won't contain double quotes).
    # The colon may have whitespace on either side.
    grep -o "\"${key}\"[[:space:]]*:[[:space:]]*\"[^\"]\+\"" |
    # Extract just <val> by deleting the last '"', and then greedily deleting
    # everything up to '"'.
    sed -e 's/"$//' -e 's/.*"//'
}

# Fetch a URL to stdout. Up to two header arguments may be specified:
#
#   fetch <url> [name1: value1] [name2: value2]
#
fetch() {
    if have_curl; then
        if [ $# -eq 2 ]; then
            set -- -H "$2" "$1"
        elif [ $# -eq 3 ]; then
            set -- -H "$2" -H "$3" "$1"
        fi
        curl -sSL "$@"
    else
        if [ $# -eq 2 ]; then
            set -- --header "$2" "$1"
        elif [ $# -eq 3 ]; then
            set -- --header "$2" --header "$3" "$1"
        fi
        wget -qO- "$@"
    fi
}

# https://docs.docker.com/docker-hub/api/latest/#tag/repositories
manifest_list_url="https://ghcr.io/v2/repositories/${image}/tags/${ref}"
if [ "$REPOSITORY_TYPE" = "ghcr" ]; then
    manifest_list_url="https://ghcr.io/v2/repositories/${image}/tags/${ref}"
elif [ "$REPOSITORY_TYPE" = "nexus" ]; then
    manifest_list_url="https://${image}/v2/repositories/tags/${ref}"
else
    echo "Unknown repository type: $REPOSITORY_TYPE"
    exit 1
fi

auth_header="Authorization: Bearer $token"
if [ "$REPOSITORY_TYPE" = "ghcr" ] ; then
    auth_header="Authorization: Bearer $token"
elif [ "$REPOSITORY_TYPE" = "nexus" ]; then
    base64_auth_string=$(echo "$NEXUS_NAME:$NEXUS_PASSWORD" | base64)
    auth_header="Authorization: Basic $base64_auth_string"
else
    echo "Unknown repository type: $REPOSITORY_TYPE"
    exit 1
fi
v2_header="Accept: application/vnd.docker.distribution.manifest.v2+json"


# If we're pulling the image for the default platform, or the ref is already
# a SHA-256 image digest, then we don't need to look up anything.
if [ "${PLATFORM}" = "${PLATFORM_DEFAULT}" ] || [ -z "${ref##sha256:*}" ]; then
    digest="${ref}"
else
    digest=$(fetch "${manifest_url} ${auth_header} ${v2_header}" |
        # Break up the single-line JSON output into separate lines by adding
        # newlines before and after the chars '[', ']', '{', and '}'.
        sed -e 's/\([][{}]\)/\n\1\n/g' |
        # Extract the "images":[...] list.
        sed -n '/"images":/,/]/ p' |
        # Each image's details are now on a separate line, e.g.
        # "architecture":"arm64","features":"","variant":"v8","digest":"sha256:054c85801c4cb41511b176eb0bf13a2c4bbd41611ddd70594ec3315e88813524","os":"linux","os_features":"","os_version":null,"size":828724,"status":"active","last_pulled":"2022-09-02T22:46:48.240632Z","last_pushed":"2022-09-02T00:42:45.69226Z"
        # The image details are interspersed with lines of stray punctuation,
        # so grep for an arbitrary string that must be in these lines.
        grep architecture |
        # Search for an image that matches the platform.
        while read -r image; do
            # Arch is probably most likely to be unique, so check that first.
            arch="$(echo ${image} | extract 'architecture')"
            if [ "${arch}" != "${ARCH}" ]; then continue; fi
            os="$(echo ${image} | extract 'os')"
            if [ "${os}" != "${OS}" ]; then continue; fi
            variant="$(echo ${image} | extract 'variant')"
            if [ "${variant}" = "${VARIANT}" ]; then
                echo ${image} | extract 'digest'
                break
            fi
        done)
fi
if [ -n "${digest}" ]; then
    echo "Platform ${PLATFORM} resolved to '${digest}'..."
else
    echo "No image digest found. Verify that the image, ref, and platform are valid."
    exit 1
fi

manifest_url="https://ghcr.io/v2/${image}/manifests/${digest}"
if [ "$REPOSITORY_TYPE" = "ghcr" ]; then
    manifest_url="https://ghcr.io/v2/${image}/manifests/${digest}"
elif [ "$REPOSITORY_TYPE" = "nexus" ]; then
    manifest_url="https://${image}/v2/manifests/${digest}"
else
    echo "Unknown repository type: $REPOSITORY_TYPE"
    exit 1
fi

blobs_base_url="https://ghcr.io/v2/${image}/blobs"
if [ "$REPOSITORY_TYPE" = "ghcr" ]; then
    blobs_base_url="https://ghcr.io/v2/${image}/blobs"
elif [ "$REPOSITORY_TYPE" = "nexus" ]; then
    blobs_base_url="https://$image/v2/blobs"
else
    echo "Unknown repository type: $REPOSITORY_TYPE"
    exit 1
fi

token=$(echo $TOKEN | base64)
auth_header="Authorization: Bearer $token"
if [ "$REPOSITORY_TYPE" = "ghcr" ]; then
    auth_header="Authorization: Bearer $token"
elif [ "$REPOSITORY_TYPE" = "nexus" ]; then
    base64_auth_string=$(echo "$NEXUS_NAME:$NEXUS_PASSWORD" | base64)
    auth_header="Authorization: Basic $base64_auth_string"
else
    echo "Unknown repository type: $REPOSITORY_TYPE"
    exit 1
fi
v2_header="Accept: application/vnd.docker.distribution.manifest.v2+json"
echo "Getting image manifest for $image:$ref..."
echo "${manifest_url}" "${auth_header}" "${v2_header}"
layers=$(fetch "${manifest_url}" "${auth_header}" "${v2_header}" |
             # Extract `digest` values only after the `layers` section appears.
             sed -n '/"layers":/,$ p' |
             extract 'digest')

if [ -z "${layers}" ]; then
    echo "No layers returned. Verify that the image and ref are valid."
    exit 1
fi
mkdir -p "${OUT_DIR}"
for i in $(seq 1 $NUMBER_OF_LAYERS); do
    n=$(($i-1))
    echo $n
    layer=$(echo $layers | awk -v n="$n" -F' ' '{print $(NF-n)}')
    echo $layer
    hash="${layer#sha256:}"
    echo "Fetching and extracting layer ${hash}..."
    fetch "${blobs_base_url}/${layer}" "${auth_header}" | gzip -d | tar -C "${OUT_DIR}" -xf -
    # Ref: https://github.com/moby/moby/blob/master/image/spec/v1.2.md#creating-an-image-filesystem-changeset
    #      https://github.com/moby/moby/blob/master/pkg/archive/whiteouts.go
    # Search for "whiteout" files to indicate files deleted in this layer.
    OLD_IFS="${IFS}"
    find "${OUT_DIR}" -name '.wh.*' | while IFS= read -r f; do
        dir="${f%/*}"
        wh_file="${f##*/}"
        file="${wh_file#.wh.}"
        # Delete both the whiteout file and the whited-out file.
        rm -rf "${dir}/${wh_file}" "${dir}/${file}"
    done
done
IFS="${OLD_IFS}"