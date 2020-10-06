#!/usr/bin/env python3.6

import socketserver
import http.server
PORT = 3000
Handler = http.server.SimpleHTTPRequestHandler

with socketserver.TCPServer(("", PORT), Handler) as httpd:
    try:
        print("serving at port", PORT)
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("Keyboard interrupt received, exiting")
    
