# MisoEyes

카메라, 기기 파일, 네트워크 이미지 URL을 가져와 기기 내 AI 이미지 인식으로 분석하고 결과를 저장하는 Android 앱입니다.

## Android Studio에서 열기

1. Android Studio를 실행합니다.
2. **Open**을 선택합니다.
3. 이 폴더(`MisoEyes`)를 선택합니다. `app` 폴더가 아니라 프로젝트 최상위 폴더를 선택해야 합니다.
4. Gradle 동기화가 끝난 뒤 실행할 기기 또는 에뮬레이터를 선택하고 Run을 누릅니다.

처음 동기화할 때 Gradle 및 라이브러리 다운로드가 필요합니다. Android SDK Platform 35와 JDK 17 이상이 필요하며, Android Studio의 내장 JDK를 사용하도록 설정되어 있습니다.

## 기능

- 카메라 촬영 및 런타임 카메라 권한 요청
- 시스템 파일 선택기를 통한 이미지 선택
- HTTPS 이미지 URL 불러오기
- Google ML Kit 기반 오프라인 이미지 라벨링
- 분석 결과 및 신뢰도 표시, 앱 내부 분석 이력 저장
- 사용 목적과 현재 상태를 안내하는 권한 확인 화면
