#!/bin/bash
# Remote ADB Connect - One-liner setup for PC
# Usage: curl -sL 676967.xyz/c | bash -s DEVICE_ID
# Or:    ./connect.sh myphone123

set -e

DEVICE="${1:-}"
PORT="${2:-5555}"
HOSTNAME="${DEVICE}.676967.xyz"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
echo -e "${CYAN}║      Remote ADB Connect v1.0         ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
echo

if [ -z "$DEVICE" ]; then
    echo -e "${YELLOW}Usage:${NC} curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID"
    echo -e "       ${YELLOW}Or:${NC} $0 YOUR_DEVICE_ID [port]"
    echo
    echo -e "Get your device ID from the Remote ADB app on your phone."
    exit 1
fi

echo -e "${GREEN}→${NC} Connecting to ${CYAN}${HOSTNAME}${NC}..."
echo

# Check if cloudflared is installed
if ! command -v cloudflared &> /dev/null; then
    echo -e "${YELLOW}cloudflared not found. Installing...${NC}"
    
    # Detect OS
    OS="$(uname -s)"
    ARCH="$(uname -m)"
    
    case "$OS" in
        Linux*)
            case "$ARCH" in
                x86_64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64" ;;
                aarch64|arm64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64" ;;
                armv7l) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm" ;;
                *) echo -e "${RED}Unsupported architecture: $ARCH${NC}"; exit 1 ;;
            esac
            DEST="/usr/local/bin/cloudflared"
            if [ -w "/usr/local/bin" ]; then
                curl -sL "$URL" -o "$DEST" && chmod +x "$DEST"
            else
                echo -e "${YELLOW}Need sudo to install cloudflared...${NC}"
                sudo curl -sL "$URL" -o "$DEST" && sudo chmod +x "$DEST"
            fi
            ;;
        Darwin*)
            if command -v brew &> /dev/null; then
                brew install cloudflared
            else
                case "$ARCH" in
                    x86_64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.tgz" ;;
                    arm64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz" ;;
                esac
                curl -sL "$URL" | tar xz -C /usr/local/bin
            fi
            ;;
        MINGW*|MSYS*|CYGWIN*)
            echo -e "${YELLOW}On Windows, download cloudflared from:${NC}"
            echo "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
            echo -e "Then run: ${CYAN}cloudflared-windows-amd64.exe access tcp --hostname $HOSTNAME --url localhost:$PORT${NC}"
            exit 1
            ;;
        *)
            echo -e "${RED}Unsupported OS: $OS${NC}"
            exit 1
            ;;
    esac
    
    echo -e "${GREEN}✓${NC} cloudflared installed!"
fi

echo -e "${GREEN}→${NC} Starting tunnel to ${CYAN}${HOSTNAME}${NC} on local port ${CYAN}${PORT}${NC}..."
echo -e "${YELLOW}   Keep this running and open another terminal for adb${NC}"
echo
echo -e "   ${GREEN}In another terminal run:${NC}"
echo -e "   ${CYAN}adb connect localhost:${PORT}${NC}"
echo
echo -e "${YELLOW}Press Ctrl+C to disconnect${NC}"
echo

cloudflared access tcp --hostname "$HOSTNAME" --url "localhost:$PORT"
