#!/usr/bin/env python3
"""
Remote ADB Shell Client - For Shizuku mode (non-root)
Connects to RemoteShellServer via TCP through Cloudflare tunnel.

Usage:
  python radb.py DEVICE_ID [command]
  python radb.py abc123 shell          # Interactive shell
  python radb.py abc123 "ls -la"       # Single command
  python radb.py abc123 push local remote
  python radb.py abc123 pull remote local

Requirements: cloudflared (auto-downloads if missing)
"""

import sys
import json
import base64
import subprocess
import os
import socket
import threading
import time

class SocketWrapper:
    """Wrapper to make socket work like websocket with send/recv for JSON lines."""
    def __init__(self, sock):
        self.sock = sock
        self.buffer = ""
    
    def send(self, data):
        self.sock.sendall((data + "\n").encode())
    
    def recv(self):
        while "\n" not in self.buffer:
            chunk = self.sock.recv(4096).decode()
            if not chunk:
                raise ConnectionError("Connection closed")
            self.buffer += chunk
        line, self.buffer = self.buffer.split("\n", 1)
        return line
    
    def close(self):
        self.sock.close()

def main():
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
    
    print(f"Connecting to {hostname}...")
    
    # Use cloudflared to tunnel TCP connection
    tunnel_port = 15555
    
    try:
        # Start cloudflared in background to tunnel TCP
        def run_cloudflared():
            try:
                subprocess.run([
                    "cloudflared", "access", "tcp",
                    "--hostname", hostname,
                    "--url", f"localhost:{tunnel_port}"
                ], capture_output=True)
            except Exception as e:
                print(f"Cloudflared error: {e}")
        
        t = threading.Thread(target=run_cloudflared, daemon=True)
        t.start()
        time.sleep(3)  # Wait for tunnel to establish
        
        # Connect via plain TCP (JSON line protocol)
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(("localhost", tunnel_port))
        sock.settimeout(30)
        
        ws = SocketWrapper(sock)
        
        # Read welcome message
        try:
            welcome = ws.recv()
            msg = json.loads(welcome)
            if msg.get("type") == "welcome":
                print(f"Connected! Mode: {msg.get('mode', 'unknown')}")
        except:
            pass
        
    except Exception as e:
        print(f"Connection failed: {e}")
        print("Make sure cloudflared is installed:")
        print("  https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/")
        sys.exit(1)
    
    if len(sys.argv) == 2 or sys.argv[2] == "shell":
        interactive_shell(ws)
    elif sys.argv[2] == "push" and len(sys.argv) >= 5:
        push_file(ws, sys.argv[3], sys.argv[4])
    elif sys.argv[2] == "pull" and len(sys.argv) >= 5:
        pull_file(ws, sys.argv[3], sys.argv[4])
    elif sys.argv[2] == "logcat":
        stream_logcat(ws)
    else:
        cmd = " ".join(sys.argv[2:])
        result = execute(ws, cmd)
        # Server returns: {success, output, stderr, exitCode}
        if result.get("output"):
            print(result["output"], end="")
        if result.get("stderr"):
            print(result["stderr"], end="", file=sys.stderr)
        sys.exit(result.get("exitCode", 0))
    
    ws.close()

def execute(ws, cmd):
    """Execute a single command and return result."""
    ws.send(json.dumps({"type": "shell", "command": cmd}))
    response = ws.recv()
    return json.loads(response)

def interactive_shell(ws):
    """Run interactive shell session."""
    try:
        import readline  # For better input handling on Unix
    except ImportError:
        pass  # Windows doesn't have readline
    
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
            if result.get("output"):
                print(result["output"], end="")
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
    if response.get("success") or response.get("type") == "ok":
        print(f"Pushed {local_path} -> {remote_path}")
    else:
        print(f"Error: {response.get('error', response.get('message', 'Unknown error'))}")
        sys.exit(1)

def pull_file(ws, remote_path, local_path):
    """Pull a file from the device."""
    ws.send(json.dumps({
        "type": "pull",
        "path": remote_path
    }))
    
    response = json.loads(ws.recv())
    if response.get("success") and response.get("data"):
        data = base64.b64decode(response["data"])
        with open(local_path, "wb") as f:
            f.write(data)
        print(f"Pulled {remote_path} -> {local_path}")
    elif response.get("type") == "file":
        data = base64.b64decode(response["data"])
        with open(local_path, "wb") as f:
            f.write(data)
        print(f"Pulled {remote_path} -> {local_path}")
    else:
        print(f"Error: {response.get('error', response.get('message', 'Unknown error'))}")
        sys.exit(1)

def stream_logcat(ws):
    """Stream logcat output."""
    print("Streaming logcat (Ctrl+C to stop)...\n")
    
    ws.send(json.dumps({
        "type": "shell",
        "command": "logcat -v time"
    }))
    
    try:
        response = json.loads(ws.recv())
        if response.get("output"):
            print(response.get("output", ""), end="")
    except KeyboardInterrupt:
        print("\nStopped")

if __name__ == "__main__":
    main()
