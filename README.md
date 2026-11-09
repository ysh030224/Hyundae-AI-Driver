# 나의 AI 연수 아저씨 (Hyundae-AI-Driver)

<table>
  <tr>
    <td width="140" align="center">
      <img src="automotive/src/main/res/drawable-nodpi/mascot_cheer.png" width="115" alt="연곰 아저씨" />
    </td>
    <td>
      <b>"안녕! 나는 연곰 아저씨야. 오늘도 나랑 같이 마음 편하게 달려보자!"</b><br/>
      초보 운전자를 위한 차량 탑재형 실시간 AI 보이스 코칭 및 운전 성장 관리 시스템<br/><br/>
      <b>나의 AI 연수 아저씨</b>는 초보 운전자가 겪는 도로 주행의 심리적 불안을 해소하고, 가족·멘토 간의 불필요한 감정 소모 없이 안전하게 운전 실력을 키울 수 있도록 돕는 <b>안드로이드 오토모티브(AAOS / Pleos Connect) 및 모바일 컴패니언 앱</b>입니다.
    </td>
  </tr>
</table>

---

## ⚡ 에뮬레이터 + 가상 디스플레이(DISPLAY2) 100% 동일 화면 실행 가이드 (4단계)

다른 PC에서도 **현대 Pleos Connect 차량 홈 화면 + FormFactorConsole + 우측 세로형 가상 디스플레이(`840 × 1184`)** 에 앱이 똑같이 실행되도록 재현하는 순서입니다.

### 1단계: `Pleos Connect v2 (API 34)` 에뮬레이터 실행
- Android Studio에서 **Pleos Connect v2 (API 34)** 에뮬레이터(AVD)를 실행합니다.
- 보조 디스플레이(`DISPLAY2`)가 옆에 표시될 수 있도록 독립 실행형(Standalone) 에뮬레이터 환경으로 실행해 주세요.

### 2단계: 호스트 디스플레이 브릿지 (`host-display-bridge`) 실행
에뮬레이터 내 `FormFactorConsole`에서 가상 디스플레이 생성 버튼이 작동하려면 호스트 PC에서 브릿지 서버가 켜져 있어야 합니다. 터미널을 열고 프로젝트 루트에서 아래 명령어를 실행합니다:

```bash
go run . --avd PleosConnect2
```
> **참고**: Go 1.22 이상이 필요하며, AVD 이름이 다를 경우 본인 에뮬레이터 이름(또는 `go run .`)으로 실행하면 자동으로 실행 중인 에뮬레이터를 연결합니다.

### 3단계: 에뮬레이터 `FormFactorConsole`에서 `DISPLAY2` 생성
1. 에뮬레이터 하단 내비게이션 바에서 **모니터 모양 아이콘 (`FormFactorConsole`)** 을 클릭합니다.
2. **Display preset**: `Connect-S · 17" · 160 DPI · 2560 × 1440` 선택
3. **Orientation**: `Portrait` (`840 × 1184 dp`) 선택
4. **`가상 디스플레이 생성`** 버튼을 클릭하면 에뮬레이터 우측에 세로형 보조 화면(`DISPLAY2`)이 생성됩니다.

### 4단계: 앱 빌드 후 우측 가상 디스플레이(`DISPLAY2`)에 띄우기
1. Android Studio에서 본 프로젝트를 열고 상단 실행 구성에서 **`automotive`** 를 선택한 뒤 **`▶ Run`** 버튼을 눌러 앱을 설치합니다.
2. Android Studio 하단 **Terminal**에 아래 명령어를 입력하면 우측 가상 디스플레이(`DISPLAY2`)에 **나의 AI 연수 아저씨** 앱이 꽉 찬 화면으로 실행됩니다:

```bash
adb shell am start -S --display 2 -n com.example.myapplication/.MainActivity
```

> 💡 **ADB 환경변수가 등록되지 않은 Windows PowerShell의 경우:**
> ```powershell
> & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -S --display 2 -n com.example.myapplication/.MainActivity
> ```

---

## 🚘 주요 기능 (Figma UI & 시스템 아키텍처)

### 1. 출발 전 (Slide 1 · 기어 P 주차 모드)
- **추천 미션 브리핑**: 멘토 지정 목표(고속도로·간선 합류, 차선 변경 안정화, 교차로 좌·우회전, 후진·평행 주차) 선택
- **연곰 아저씨의 한마디**: 핵심 주행 팁 사전 음성 안내 및 도로 약도 클립보드 안내 포즈(`mascot_guide`) 제공
- **경로 위 주의 구간 사전 예고**: 톨게이트 감속 구간, 분기점 합류 구간 등 지도 API 기반 사전 안내

### 2. 주행 중 (Slide 2 · 기어 D 주행 모드)
- **주행 중 조작 차단 (Distraction Free)**: 복잡한 텍스트를 숨기고 음성 안내에 집중할 수 있는 보이스 파형 HUD (`Set up while parked. Drive at your pace.`)
- **반응형 음향 파형 (Acoustic Wave)**: 좌우 동심원 민트색 음파 애니메이션으로 음성 발화 상태 시각화
- **실시간 캐릭터 자세/표정 변형 (Dynamic Pose Transformation)**:
  - **평상시**: 인자한 미소의 상반신 클로즈업 포즈 (`mascot_closeup`)
  - **합류/출발 안내**: 한 손 주먹을 쥐고 응원하는 파이팅 포즈 (`mascot_cheer`)
  - **차간거리 근접/급제동 위험**: 두 손바닥을 들어 주의를 주는 정지 포즈 + 경고 레드 음파 (`mascot_caution`)
  - **차선 변경/합류 성공**: 양손 엄지척(쌍따봉) 칭찬 포즈 (`mascot_praise`)

### 3. 주행 완료 및 안정성 분석 (Slide 3 · 기어 P 주차 모드)
- **차량 센서/API 기반 3대 안전 지표 (100점 만점)**:
  - **급제동 제어 (82점)**: 급제동 감지 및 사전 브레이크 권장
  - **조향/차선 변경 (94점)**: 부드럽고 안정적인 스티어링 조작
  - **속도 준수율 (100점)**: 전 구간 규정 속도 완벽 준수
- **멘토 피드백 카드**: 가족 멘토(아버지 등)의 따뜻한 조언 및 음성 듣기
- **정차 중 AI Q&A**: 주행 중 궁금했던 점을 주차 상태에서 연곰 아저씨에게 직접 질문

### 4. 멘토 연결 & 프라이버시 설정 (Slide 4, 5)
- **초대 코드 시스템**: 간편한 초대 코드(`H-8294`) 복사 및 공유
- **선택적 데이터 공유**: 실시간 GPS 위치는 전송하지 않으며, 급제동 횟수/주행 점수/경로 요약만 안심 공유

### 5. 마이 드라이빙 & 초보 운전자 졸업 (Slide 6, 7)
- **졸업 미션 5단계 달성 진행도**:
  1. 첫 안전 주행 완료 (안전의 첫걸음)
  2. 규정 속도 지키기 (전 구간 속도 준수)
  3. 부드러운 차선 변경 (주행 안정성 90점 이상)
  4. 고속도로 합류 도전 (본선 흐름에 맞춰 합류하기)
  5. 혼자서 주차 성공 (출발부터 주차까지 스스로)
- **초보 탈출 축하 및 멘토 전환**: 5/5 미션 달성 시 졸업 축하 모달 및 새로운 후배 운전자를 이끌어주는 멘토 모드 활성화

---

## 🛠 모듈 구성 & 한국어 음성(TTS) 시스템

### 안드로이드 모듈 구조
- **`:automotive`**: 차량용 인포테인먼트(IVI / AAOS / Pleos Connect) 앱 (대화면 840×1184 세로형 디스플레이 최적화)
- **`:mobile`**: 스마트폰용 컴패니언 앱 (마이 드라이빙, 성장 기록, 멘토 피드백 확인)
- **`:shared`**: 공통 비즈니스 로직 및 안드로이드 오토 Car App Library 연동 모듈

### 하이브리드 한국어 음성 (TTS) 코칭
- 차량 내 Google TTS 및 시스템 기본 한국어 음성 엔진 자동 탐색
- 주행 안전 지침을 준수하여 **주행 중에는 20자 이내의 짧고 명확한 한마디 코칭** 제공
- 주차 중에는 상세한 사전 브리핑 및 Q&A 대화형 음성 제공

---

## 🖥️ ConnectWebConsole 호스트 디스플레이 브릿지 상세 명세

이 프로세스는 에뮬레이터 호스트 PC에서 실행되며, Android 에뮬레이터의 보조 디스플레이(`DISPLAY2`)를 생성·제거하기 위한 로컬 루프백(loopback) 전용 HTTP API를 제공합니다. 기존의 `ConnectWebConsole WindowPolicy API` 요청을 Android 에뮬레이터의 `getDisplayConfigurations` 및 `setDisplayConfigurations` gRPC 호출로 변환해 줍니다.

### 요구 사항 (Requirements)
- 멀티 디스플레이가 활성화된 **Android Emulator 34.1.1 이상**
- 소스에서 직접 빌드할 경우 **Go 1.22 이상**
- **독립 실행형(Standalone) 에뮬레이터 창** (Android Studio 내부에 고정된 임베디드/헤드리스 에뮬레이터 창에서는 보조 디스플레이 창을 표시할 수 없습니다)

이 브릿지는 Windows, macOS, Ubuntu/Linux(amd64 및 arm64)를 모두 지원하며, 제공된 스크립트로 빌드할 경우 별도의 런타임 의존성이 필요하지 않습니다.

### 브릿지 고급 실행 옵션
에뮬레이터 검색 파일 경로를 직접 지정할 경우:

```bash
connect-display-bridge --endpoint-file /path/to/pid_1234.ini --avd PleosConnect2
```

에뮬레이터가 `grpc.token`을 발행하지 않는 경우, 토큰 인증을 활성화하여 에뮬레이터를 시작하세요:

```bash
emulator -avd PleosConnect2 -grpc 8554 -grpc-use-token
```

격리된 개발 환경에서는 비보안 gRPC 엔드포인트로 실행된 에뮬레이터에 직접 연결할 수도 있습니다:

```bash
connect-display-bridge --emulator-address 127.0.0.1:8554
```

기본 HTTP 엔드포인트는 `http://127.0.0.1:8096`이며, 보안을 위해 루프백이 아닌 외부 바인드 주소 및 브라우저 출처(Origin)는 거부합니다.

### API 사용 예시

```bash
# 상태 확인 및 디스플레이 조회
curl http://127.0.0.1:8096/health
curl http://127.0.0.1:8096/api/window-policy/display

# 가상 디스플레이(DISPLAY2, 세로형 840x1184dp) 생성
curl -X PUT http://127.0.0.1:8096/api/window-policy/display/create \
  -H 'Content-Type: application/json' \
  -d '{"dpi":160,"width":2560,"height":1440,"orientation":"portrait"}'

# 가상 디스플레이 제거
curl -X PUT http://127.0.0.1:8096/api/window-policy/display/remove \
  -H 'Content-Type: application/json' -d '{}'
```

`crp_avd`는 슬롯 1을 계기판(클러스터)용으로 예약하므로 `DISPLAY2`는 에뮬레이터 **슬롯 2**를 사용합니다.

### 플랫폼별 브릿지 전체 빌드 (Build all platforms)

Linux / macOS / Git Bash:
```bash
./scripts/build-all.sh
```

Windows PowerShell:
```powershell
.\scripts\build-all.ps1
```
빌드된 실행 파일은 `dist/` 폴더에 생성됩니다.

### 브라우저 UI 연결 (Connect the browser UI)
WindowPolicy 페이지는 ADB를 통해 `http://127.0.0.1:8095`에서 제공되며, 디스플레이 제어 요청은 아래 브릿지 주소로 전송됩니다:

```text
http://127.0.0.1:8096/api/window-policy/display
```

다른 브릿지 포트를 사용하려면 브라우저 콘솔에서 아래와 같이 설정한 후 페이지를 새로고침하세요:

```javascript
localStorage.setItem('connectDisplayBridgeOrigin', 'http://127.0.0.1:18096');
```
