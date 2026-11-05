"""Check the production app-auth entry without following redirects or signing in.

Only status, provider host and cookie attributes are retained. Authorization URLs,
state, cookie values and PKCE material must not be printed or saved.
"""
import base64
import hashlib
import json
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    base = "https://jungsan-api.devkdk.com"
    opener = urllib.request.build_opener(NoRedirect)
    results = []
    for provider, expected_host in [("apple", "appleid.apple.com"), ("kakao", "kauth.kakao.com")]:
        verifier = secrets.token_urlsafe(48)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode("ascii")).digest()).decode("ascii").rstrip("=")
        query = urllib.parse.urlencode({"client": "app", "appReturn": "session", "codeChallenge": challenge})
        request = urllib.request.Request(f"{base}/api/v1/auth/{provider}/login?{query}")
        try:
            response = opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, TimeoutError):
            raise SystemExit(f"Auth start unavailable: {provider}") from None
        with response:
            location = urllib.parse.urlparse(response.headers.get("Location", ""))
            parameters = urllib.parse.parse_qs(location.query)
            cookie_attributes = [value.lower().split("; ")[1:] for value in response.headers.get_all("Set-Cookie", [])]
            expected_site = "samesite=none" if provider == "apple" else "samesite=lax"
            result = {
                "provider": provider,
                "status": response.code,
                "providerHost": location.hostname,
                "expectedHttpsProvider": location.scheme == "https" and location.hostname == expected_host,
                "expectedCallback": parameters.get("redirect_uri") == [f"{base}/api/v1/auth/{provider}/callback"],
                "secureHttpOnlyBinding": any("secure" in attrs and "httponly" in attrs and expected_site in attrs for attrs in cookie_attributes),
                "noStore": "no-store" in response.headers.get("Cache-Control", "").lower(),
            }
            results.append(result)
    print(json.dumps(results, ensure_ascii=False, indent=2))
    if any(result["status"] != 302 or not all(result[key] for key in
           ["expectedHttpsProvider", "expectedCallback", "secureHttpOnlyBinding", "noStore"]) for result in results):
        sys.exit(1)


if __name__ == "__main__":
    main()
