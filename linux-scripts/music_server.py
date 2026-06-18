#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import os

class MediaHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Керування гучністю через системний мікшер CachyOS (ALSA/PipeWire)
        if self.path == "/media/volume_up":
            os.system("amixer set Master 5%+")
        elif self.path == "/media/volume_down":
            os.system("amixer set Master 5%-")
        elif self.path == "/media/toggle":
            os.system("playerctl play-pause")
        
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def log_message(self, format, *args):
        return

# Слухаємо порт 9090
server = HTTPServer(('0.0.0.0', 9090), MediaHandler)
server.serve_forever()
