import http.server
import socketserver
import threading
import socket
import os
import sys
import tempfile

PORT = 8000
_default_dir = os.path.dirname(os.path.abspath(__file__))
_temp_spike = os.path.join(tempfile.gettempdir(), "emberbyte_spike")
if not os.path.exists(os.path.join(_default_dir, "small.bin")) and os.path.exists(os.path.join(_temp_spike, "small.bin")):
    _default_dir = _temp_spike
SERVE_DIR = os.environ.get("SPIKE_SERVE_DIR", _default_dir)

class SpikeHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=SERVE_DIR, **kwargs)

    def do_PUT(self):
        length = int(self.headers.get('Content-Length', 0))
        remaining = length
        while remaining > 0:
            chunk = self.rfile.read(min(remaining, 65536))
            if not chunk:
                break
            remaining -= len(chunk)
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', '2')
        self.end_headers()
        self.wfile.write(b"OK")

    def log_message(self, format, *args):
        pass

def run_udp_echo():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('0.0.0.0', PORT))
    while True:
        try:
            data, addr = sock.recvfrom(65535)
            sock.sendto(data, addr)
        except Exception:
            break

if __name__ == '__main__':
    udp_thread = threading.Thread(target=run_udp_echo, daemon=True)
    udp_thread.start()
    print(f"Spike server serving {SERVE_DIR} on port {PORT} (TCP/UDP)")
    with socketserver.TCPServer(('0.0.0.0', PORT), SpikeHandler) as httpd:
        httpd.serve_forever()
