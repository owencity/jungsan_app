"""UI 테스트 러너의 동기 UIKit 읽기 대신, 시뮬레이터 클립보드 계약을 호스트에서 검증한다."""

import argparse
import hashlib
import subprocess
import sys
import time


EXPECTED = (
    "정산어택 데모 체험 (가상 데이터, 실제 송금 없음)\n"
    "https://jungsan-api.devkdk.com/api/v1/review/demo\n"
    "앱에서 총무·참여자 역할을 전환해보세요."
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("seed", "assert"))
    parser.add_argument("device")
    parser.add_argument("case")
    args = parser.parse_args()
    command = "pbcopy" if args.mode == "seed" else "pbpaste"
    for attempt in range(2):
        try:
            result = subprocess.run(
                ["xcrun", "simctl", command, args.device],
                input=f"before-share:{args.case}" if args.mode == "seed" else None,
                capture_output=True, text=True, encoding="utf-8", check=True, timeout=20,
            )
            break
        except subprocess.CalledProcessError as error:
            # 앱 동작 전 simctl 서비스의 일시적 ETIMEDOUT만 한 번 재시도한다.
            # 실제 클립보드 값 불일치와 다른 오류는 재시도/성공으로 바꾸지 않는다.
            if error.returncode == 60 and attempt == 0:
                print(f"::warning::Simulator clipboard {command} transport ETIMEDOUT; retry once")
                time.sleep(1)
                continue
            print(f"::error::Simulator clipboard {command} failed: exit={error.returncode}, stderr={error.stderr[:500]}")
            return 1
        except subprocess.TimeoutExpired as error:
            print(f"::error::Simulator clipboard {command} failed: {type(error).__name__}")
            return 1
    if args.mode == "assert":
        actual = result.stdout.rstrip("\r\n")
        if actual != EXPECTED:
            # 클립보드 원문은 로그에 남기지 않는다. 다른 테스트의 잔여 값도 성공으로 처리하지 않는다.
            digest = hashlib.sha256(actual.encode("utf-8")).hexdigest()
            print(f"::error::Clipboard contract mismatch: {args.case}, length={len(actual)}, sha256={digest}")
            return 1
        print(f"Clipboard contract PASS: {args.case}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
