# Polityka Prywatności

Aplikacja mobilna **eDziennikus** — community-fork projektu
[Szkolny.eu](https://szkolny.eu) autorstwa Kuby Szczodrzyńskiego,
dystrybuowany poprzez
[GitHub Releases](https://github.com/mikus/eDziennikus/releases).

## § 1 Postanowienia ogólne

1. Niniejsza Polityka Prywatności określa sposób zbierania,
   przetwarzania i przechowywania danych przez aplikację mobilną
   eDziennikus (zwaną dalej „Aplikacją").
2. Aplikacja jest forkiem (rozwidleniem) open-source projektu
   Szkolny.eu, utrzymywanym przez [mikus](https://github.com/mikus)
   i dystrybuowanym **wyłącznie** poprzez GitHub Releases. Aplikacja
   nie jest oferowana w sklepie Google Play.
3. W odróżnieniu od oryginalnej aplikacji Szkolny.eu, eDziennikus
   **nie zawiera**:
   - Firebase Analytics, Crashlytics ani żadnych innych mechanizmów
     telemetrii ani analityki firm trzecich;
   - centralnej infrastruktury serwerowej autora — Aplikacja nie
     komunikuje się z serwerami autora ani z serwerami Szkolny.eu.
4. Użytkownikiem jest każda osoba fizyczna korzystająca z Aplikacji
   na swoim urządzeniu mobilnym z systemem Android.

## § 2 Komunikacja sieciowa

Aplikacja inicjuje komunikację sieciową wyłącznie z dwoma rodzajami
zewnętrznych usług:

1. **Serwery wybranego systemu e-dziennika** — Aplikacja komunikuje się
   z serwerami wybranego przez Użytkownika systemu e-dziennika, w celu
   pobierania danych Użytkownika z dziennika. Dane uwierzytelniające
   (login, hasło) oraz odpowiedzi serwerów dziennika nie są przekazywane
   do żadnego innego podmiotu.
2. **GitHub** — Aplikacja może komunikować się z `api.github.com` przy
   sprawdzaniu dostępności aktualizacji w
   [GitHub Releases](https://github.com/mikus/eDziennikus/releases).
   GitHub może zarejestrować adres IP oraz User-Agent urządzenia —
   zgodnie z
   [polityką prywatności GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## § 3 Przechowywanie danych lokalnie

1. Wszystkie dane Użytkownika (zawartość dziennika, ustawienia,
   profile kont) są przechowywane **lokalnie** na urządzeniu Użytkownika,
   w bazie danych Room (SQLite).
2. Autor Aplikacji nie utrzymuje żadnej infrastruktury serwerowej
   przechowującej dane Użytkowników — wszystkie dane lokalne pozostają
   pod wyłączną kontrolą Użytkownika.
3. Odinstalowanie Aplikacji powoduje trwałe usunięcie wszystkich
   lokalnie zapisanych danych.

## § 4 Uprawnienia systemowe

Aplikacja korzysta z następujących uprawnień systemu Android:

- **INTERNET, ACCESS_NETWORK_STATE** — komunikacja z serwerami systemu
  e-dziennika oraz opcjonalnie z GitHub przy sprawdzaniu aktualizacji.
- **READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE** — zapis załączników
  z dziennika i ustawień Aplikacji.
- **CAMERA** *(opcjonalnie)* — wykonanie zdjęcia awatara Profilu oraz
  skanowanie kodu QR przy funkcji „Przekazywanie powiadomień". Aplikacja
  nie uruchomi kamery bez akcji Użytkownika.
- **RECEIVE_BOOT_COMPLETED, WAKE_LOCK, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_DATA_SYNC** — automatyczna synchronizacja danych
  z dziennika w tle, w tym po restarcie urządzenia.
- **POST_NOTIFICATIONS, VIBRATE** — powiadomienia o nowych ocenach,
  wiadomościach itp.
- **REQUEST_INSTALL_PACKAGES** — instalacja aktualizacji Aplikacji
  pobranych z GitHub Releases (wymaga wyraźnej akceptacji Użytkownika
  w systemie Android).

## § 5 Prawa Użytkownika

1. Użytkownik ma dostęp do wszystkich zapisanych przez Aplikację
   danych poprzez sam interfejs Aplikacji.
2. Użytkownik może w każdej chwili usunąć swój profil w Aplikacji,
   co kasuje wszystkie powiązane dane lokalne.
3. Odinstalowanie Aplikacji jest pełnym zrealizowaniem prawa do
   bycia zapomnianym po stronie Aplikacji — autor nie posiada żadnych
   kopii danych Użytkownika.

## § 6 Zgłoszenia i kontakt

Pytania, błędy oraz zgłoszenia dotyczące Polityki Prywatności prosimy
zgłaszać przez [GitHub Issues](https://github.com/mikus/eDziennikus/issues).

## § 7 Zmiany w Polityce Prywatności

Autor zastrzega sobie prawo wprowadzania zmian w niniejszej Polityce
Prywatności. Aktualna wersja jest zawsze dostępna w repozytorium
Aplikacji pod adresem
[github.com/mikus/eDziennikus/blob/main/PRIVACY.md](https://github.com/mikus/eDziennikus/blob/main/PRIVACY.md).
Data ostatniej aktualizacji jest widoczna w historii zmian pliku
(`git log PRIVACY.md`).
