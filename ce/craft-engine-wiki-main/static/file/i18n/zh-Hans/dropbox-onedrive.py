import json
import threading
import time
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
import requests

# 全局变量存储授权码
AUTH_CODE = None
SERVICE_TYPE = None
CLIENT_ID = ""
CLIENT_SECRET = None
REDIRECT_URI = None


class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        global AUTH_CODE
        # 解析URL中的查询参数
        query = urlparse(self.path).query
        params = parse_qs(query)

        # 检查是否包含授权码
        if "code" in params:
            AUTH_CODE = params["code"][0]
            self.send_response(200)
            self.send_header("Content-type", "text/html")
            self.end_headers()
            self.wfile.write(
                b"<h1>Authentication Successful!</h1><p>You can close this window.</p>"
            )
            # 延迟关闭服务器
            threading.Thread(target=self.server.shutdown, daemon=True).start()
        else:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing authorization code")


def start_server():
    """启动HTTP服务器监听回调"""
    server_address = ("localhost", 8080)
    httpd = HTTPServer(server_address, CallbackHandler)
    print("Server started at http://localhost:8080")
    httpd.serve_forever()


def get_dropbox_token():
    """获取Dropbox刷新令牌"""
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
    """获取OneDrive刷新令牌"""
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

    print("选择云存储服务:")
    print("1. Dropbox")
    print("2. OneDrive")
    choice = input("输入选项 (1/2): ").strip()

    if choice == "1":
        SERVICE_TYPE = "Dropbox"
        # Dropbox OAuth参数
        auth_url = "https://www.dropbox.com/oauth2/authorize"
        params = {
            "response_type": "code",
            "client_id": "",
            "redirect_uri": "http://localhost:8080",
            "token_access_type": "offline",
        }
    elif choice == "2":
        SERVICE_TYPE = "OneDrive"
        # OneDrive OAuth参数
        auth_url = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        params = {
            "client_id": "",
            "response_type": "code",
            "redirect_uri": "http://localhost:8080",
            "scope": "Files.ReadWrite.All Files.Read.All offline_access",
            "prompt": "select_account",
        }
    else:
        print("无效选项")
        return

    # 获取用户输入
    if SERVICE_TYPE == "Dropbox":
        CLIENT_ID = input(f"请输入{SERVICE_TYPE}的App key: ").strip()
        CLIENT_SECRET = input(f"请输入{SERVICE_TYPE}的App secret: ").strip()
    elif SERVICE_TYPE == "OneDrive":
        CLIENT_ID = input(f"请输入{SERVICE_TYPE}的Client ID: ").strip()
        CLIENT_SECRET = input(f"请输入{SERVICE_TYPE}的Client Secret: ").strip()
    REDIRECT_URI = "http://localhost:8080"

    # 更新授权URL参数
    params["client_id"] = CLIENT_ID

    # 启动HTTP服务器
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()
    time.sleep(1)  # 确保服务器启动

    # 打开浏览器进行认证
    query_str = "&".join([f"{k}={v}" for k, v in params.items()])
    full_auth_url = f"{auth_url}?{query_str}"
    print(f"请在弹出的浏览器窗口中登录{SERVICE_TYPE}账号...")
    webbrowser.open(full_auth_url)

    # 等待授权码
    while AUTH_CODE is None:
        time.sleep(1)

    # 获取刷新令牌
    try:
        if SERVICE_TYPE == "Dropbox":
            refresh_token = get_dropbox_token()
        else:
            refresh_token = get_onedrive_token()

        # 输出结果
        print(f"\n获取到{SERVICE_TYPE}刷新令牌:")
        print(refresh_token)

        # 保存到文件
        filename = f"{SERVICE_TYPE.lower()}_refresh_token.txt"
        with open(filename, "w", encoding="utf-8") as f:
            f.write(refresh_token)
        print(f"令牌已保存到: {filename}")

    except Exception as e:
        print(f"获取令牌失败: {str(e)}")


if __name__ == "__main__":
    main()
