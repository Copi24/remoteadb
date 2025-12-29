#!/usr/bin/env python3
"""
Remote ADB Shell Client - For Shizuku mode (non-root)
Connects to RemoteShellServer via WebSocket through Cloudflare tunnel.

Usage:
  python radb.py DEVICE_ID [command]
  python radb.py abc123 shell          # Interactive shell
  python radb.py abc123 "ls -la"       # Single command
  python radb.py abc123 push local remote
  python radb.py abc123 pull remote local

Requirements: pip install websocket-client
"""

import sys
import json
import base64
import subprocess
import os

def install_deps():
    """Auto-install websocket-client if missing."""
    try:
        import websocket
        return websocket
    except ImportError:
        print("Installing websocket-client...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket
        return websocket

def main():
    websocket = install_deps()
    
    if len(sys.argv) < 2:
        print("Remote ADB Shell Client")
        print("Usage:")
        print("  radb DEVICE_ID shell          - Interactive shell")
        print("  radb DEVICE_ID 'command'      - Run single command")
        print("  radb DEVICE_ID push src dst   - Push file to device")
        print("  radb DEVICE_ID pull src dst   - Pull file from device")
        print("  radb DEVICE_ID logcat         - Stream logcat")
        sys.exit(1)
    
    device_id = sys.argv[1]
    hostname = f"{device_id}.676967.xyz"
    
    # Check if cloudflared is available and start tunnel
    print(f"Connecting to {hostname}...")
    
    # For Shizuku mode, we connect directly to the WebSocket server
    # The tunnel exposes port 5556 (RemoteShellServer)
    ws_url = f"wss://{hostname}:5556"
    
    try:
        # Try direct WebSocket first (if tunnel supports it)
        ws = websocket.create_connection(ws_url, timeout=10)
    except:
        # Fall back to using cloudflared access
        print("Direct connection failed, using cloudflared tunnel...")
        print("Note: For Shizuku mode, ensure the tunnel is configured for port 5556")
        
        # Start cloudflared in background
        tunnel_port = 15556
        import threading
        import time
        
        def run_cloudflared():
            subprocess.run([
                "cloudflared", "access", "tcp",
                "--hostname", hostname,
                "--url", f"localhost:{tunnel_port}"
            ], capture_output=True)
        
        t = threading.Thread(target=run_cloudflared, daemon=True)
        t.start()
        time.sleep(2)  # Wait for tunnel
        
        ws = websocket.create_connection(f"ws://localhost:{tunnel_port}", timeout=10)
    
    print("Connected!")
    
    if len(sys.argv) == 2 or sys.argv[2] == "shell":
        # Interactive shell
        interactive_shell(ws)
    elif sys.argv[2] == "push" and len(sys.argv) >= 5:
        push_file(ws, sys.argv[3], sys.argv[4])
    elif sys.argv[2] == "pull" and len(sys.argv) >= 5:
        pull_file(ws, sys.argv[3], sys.argv[4])
    elif sys.argv[2] == "logcat":
        stream_logcat(ws)
    else:
        # Single command
        cmd = " ".join(sys.argv[2:])
        result = execute(ws, cmd)
        if result.get("stdout"):
            print(result["stdout"], end="")
        if result.get("stderr"):
            print(result["stderr"], end="", file=sys.stderr)
        sys.exit(result.get("exit", 0))
    
    ws.close()

def execute(ws, cmd):
    """Execute a single command and return result."""
    ws.send(json.dumps({"type": "shell", "cmd": cmd}))
    response = ws.recv()
    return json.loads(response)

def interactive_shell(ws):
    """Run interactive shell session."""
    import readline  # For better input handling on Unix
    
    print("Remote ADB Shell (Shizuku mode)")
    print("Type 'exit' to quit\n")
    
    while True:
        try:
            cmd = input("$ ")
            if cmd.strip().lower() in ("exit", "quit"):
                break
            if not cmd.strip():
                continue
            
            result = execute(ws, cmd)
            if result.get("stdout"):
                print(result["stdout"], end="")
            if result.get("stderr"):
                print(result["stderr"], end="")
        except KeyboardInterrupt:
            print("\nUse 'exit' to quit")
        except EOFError:
            break

def push_file(ws, local_path, remote_path):
    """Push a file to the device."""
    if not os.path.exists(local_path):
        print(f"Error: {local_path} not found")
        sys.exit(1)
    
    with open(local_path, "rb") as f:
        data = base64.b64encode(f.read()).decode()
    
    ws.send(json.dumps({
        "type": "push",
        "path": remote_path,
        "data": data
    }))
    
    response = json.loads(ws.recv())
    if response.get("type") == "ok":
        print(f"Pushed {local_path} -> {remote_path}")
    else:
        print(f"Error: {response.get('message', 'Unknown error')}")
        sys.exit(1)

def pull_file(ws, remote_path, local_path):
    """Pull a file from the device."""
    ws.send(json.dumps({
        "type": "pull",
        "path": remote_path
    }))
    
    response = json.loads(ws.recv())
    if response.get("type") == "file":
        data = base64.b64decode(response["data"])
        with open(local_path, "wb") as f:
            f.write(data)
        print(f"Pulled {remote_path} -> {local_path}")
    else:
        print(f"Error: {response.get('message', 'Unknown error')}")
        sys.exit(1)

def stream_logcat(ws):
    """Stream logcat output."""
    print("Streaming logcat (Ctrl+C to stop)...\n")
    
    ws.send(json.dumps({
        "type": "stream",
        "cmd": "logcat -v time"
    }))
    
    try:
        while True:
            response = json.loads(ws.recv())
            if response.get("type") == "line":
                print(response.get("text", ""))
            elif response.get("type") == "output":
                # Full output returned
                print(response.get("stdout", ""), end="")
                break
    except KeyboardInterrupt:
        print("\nStopped")

if __name__ == "__main__":
    main()
