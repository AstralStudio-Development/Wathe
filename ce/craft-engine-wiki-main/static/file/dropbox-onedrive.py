import json
import threading
import time
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
import requests

# Global variables to store authorization code
AUTH_CODE = None
SERVICE_TYPE = None
CLIENT_ID = ""
CLIENT_SECRET = None
REDIRECT_URI = None


class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        global AUTH_CODE
        # Parse query parameters from URL
        query = urlparse(self.path).query
        params = parse_qs(query)

        # Check if authorization code is included
        if "code" in params:
            AUTH_CODE = params["code"][0]
            self.send_response(200)
            self.send_header("Content-type", "text/html")
            self.end_headers()
            self.wfile.write(
                b"<h1>Authentication Successful!</h1><p>You can close this window.</p>"
            )
            # Delay server shutdown
            threading.Thread(target=self.server.shutdown, daemon=True).start()
        else:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing authorization code")


def start_server():
    """Start HTTP server to listen for callback"""
    server_address = ("localhost", 8080)
    httpd = HTTPServer(server_address, CallbackHandler)
    print("Server started at http://localhost:8080")
    httpd.serve_forever()


def get_dropbox_token():
    """Get Dropbox refresh token"""
    token_url = "https://api.dropboxapi.com/oauth2/token"
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    data = {
        "code": AUTH_CODE,
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "redirect_uri": REDIRECT_URI,
    }

    response = requests.post(token_url, headers=headers, data=data, timeout=60)
    if response.status_code == 200:
        token_data = response.json()
        return token_data.get("refresh_token")
    else:
        raise Exception(f"Error: {response.status_code}, {response.text}")


def get_onedrive_token():
    """Get OneDrive refresh token"""
    token_url = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    data = {
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "code": AUTH_CODE,
        "redirect_uri": REDIRECT_URI,
        "grant_type": "authorization_code",
    }

    response = requests.post(token_url, headers=headers, data=data, timeout=60)
    if response.status_code == 200:
        token_data = response.json()
        return token_data.get("refresh_token")
    else:
        raise Exception(f"Error: {response.status_code}, {response.text}")


def main():
    global SERVICE_TYPE, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI

    print("Select cloud storage service:")
    print("1. Dropbox")
    print("2. OneDrive")
    choice = input("Enter choice (1/2): ").strip()

    if choice == "1":
        SERVICE_TYPE = "Dropbox"
        # Dropbox OAuth parameters
        auth_url = "https://www.dropbox.com/oauth2/authorize"
        params = {
            "response_type": "code",
            "client_id": "",
            "redirect_uri": "http://localhost:8080",
            "token_access_type": "offline",
        }
    elif choice == "2":
        SERVICE_TYPE = "OneDrive"
        # OneDrive OAuth parameters
        auth_url = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        params = {
            "client_id": "",
            "response_type": "code",
            "redirect_uri": "http://localhost:8080",
            "scope": "Files.ReadWrite.All Files.Read.All offline_access",
            "prompt": "select_account",
        }
    else:
        print("Invalid choice")
        return

    # Get user input
    if SERVICE_TYPE == "Dropbox":
        CLIENT_ID = input(f"Enter {SERVICE_TYPE}'s App key: ").strip()
        CLIENT_SECRET = input(f"Enter {SERVICE_TYPE}'s App secret: ").strip()
    elif SERVICE_TYPE == "OneDrive":
        CLIENT_ID = input(f"Enter {SERVICE_TYPE}'s Client ID: ").strip()
        CLIENT_SECRET = input(f"Enter {SERVICE_TYPE}'s Client Secret: ").strip()
    REDIRECT_URI = "http://localhost:8080"

    # Update authorization URL parameters
    params["client_id"] = CLIENT_ID

    # Start HTTP server
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()
    time.sleep(1)  # Ensure server starts

    # Open browser for authentication
    query_str = "&".join([f"{k}={v}" for k, v in params.items()])
    full_auth_url = f"{auth_url}?{query_str}"
    print(f"Please log in to your {SERVICE_TYPE} account in the pop-up browser window...")
    webbrowser.open(full_auth_url)

    # Wait for authorization code
    while AUTH_CODE is None:
        time.sleep(1)

    # Get refresh token
    try:
        if SERVICE_TYPE == "Dropbox":
            refresh_token = get_dropbox_token()
        else:
            refresh_token = get_onedrive_token()

        # Output result
        print(f"\nObtained {SERVICE_TYPE} refresh token:")
        print(refresh_token)

        # Save to file
        filename = f"{SERVICE_TYPE.lower()}_refresh_token.txt"
        with open(filename, "w", encoding="utf-8") as f:
            f.write(refresh_token)
        print(f"Token saved to: {filename}")

    except Exception as e:
        print(f"Failed to get token: {str(e)}")


if __name__ == "__main__":
    main()
