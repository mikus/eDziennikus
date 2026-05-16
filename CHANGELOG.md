# Changelog

Wszystkie istotne zmiany w **eDziennikus** są dokumentowane w tym pliku.

Format opiera się na konwencji [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/),
a aplikacja używa kalendarzowego schematu wersjonowania (`YYYY.MM.patch`):
miesiąc i rok określają datę wydania, `patch` zaczyna się od `0` i rośnie
przy poprawkach w obrębie tego samego miesiąca (np. `2026.05.0` → `2026.05.1`).

## [2026.05.0] — 2026-05-15

Pierwsze wydanie forka **eDziennikus** — pochodnej projektu
[szkolny-eu/szkolny-android](https://github.com/szkolny-eu/szkolny-android),
zawężonej do jednego backendu e-dziennika, przepakowanej pod
`eu.mikus.edziennik` i rozprowadzanej wyłącznie przez GitHub Releases.

Punkt rozejścia względem upstream'u:
[`1f712dbf`](https://github.com/szkolny-eu/szkolny-android/commit/1f712dbf231b70767a396d3113434417dde4c278)
(„Merge branch 'develop-v4'", 2025-11-26).

### Tożsamość forka

- Identyfikator aplikacji zmieniony: `eu.szkolny.app` → `eu.mikus.edziennik`.
- Nazwa aplikacji: Szkolny.eu → eDziennikus.
- Ikona launchera i powiadomień przerysowana jako glif „eD" (vector drawable).
- Przyjęto kalendarzowe wersjonowanie (`YYYY.MM.patch`).
- Własna tożsamość podpisu (osobny keystore); BuildManager rozpoznaje
  podpisany build forka i wyświetla badge „Release" zamiast „Unofficial".
- Własna infrastruktura CI: weryfikacja `assembleDebug` na każdy push,
  zbudowanie i opublikowanie podpisanego APK przy push'u tagu `v*`.

### Zawężony zakres

- Pozostawiono tylko jeden aktywny backend e-dziennika; pozostałe
  providery z upstream'u zostały usunięte (provider `demo/` zachowano
  na potrzeby zrzutów ekranu i testów offline).
- Pojedyncza konfiguracja build'a (trio flavorów
  `unofficial`/`official`/`play` zlikwidowane, source-set `play-not`
  scalony do `main/`).
- Firebase Analytics, Crashlytics oraz FCM całkowicie usunięte —
  brak telemetrii, analityki i integracji z usługami stron trzecich.
- Cała integracja z SzkolnyApi (centralny backend `szkolny.eu`)
  wyrugowana. Aplikacja nie kontaktuje się już z infrastrukturą
  Szkolny.eu w żadnej operacji.
- Native code (`app/src/main/cpp/` + biblioteka JNI `szkolny-signing`)
  usunięty wraz z SzkolnyApi — był używany wyłącznie do podpisywania
  żądań do `szkolny.eu`.
- Schemat bazy danych Room wyczyszczony: kolumny `sharedBy`,
  `sharedByName`, `registration`, `enableSharedEvents` oraz powiązane
  pola (`canShare`) usunięte. Czysty `AppDb` v1.

### Dodano

- `PRIVACY.md` — polityka prywatności forka dostosowana do faktycznego
  zakresu danych (bez Firebase, bez serwerów `szkolny.eu`).
- Sprawdzanie aktualizacji oparte o GitHub Releases API
  (`api.github.com/repos/mikus/eDziennikus/releases/latest`).
  Zastąpiło wcześniejsze odpytywanie endpointu `szkolny.eu/update`.
- Karta „Dostępna nowa wersja" na ekranie głównym pojawia się
  gdy najnowsze GitHub Release ma wyższy `versionCode`.

### Zmieniono

- Sekcja „O aplikacji": ikona zaktualizowana do glifu „eD",
  podpis copyright zaktualizowany („© mikus 2026 — fork projektu
  Szkolny.eu (© Kuba Szczodrzyński 2018)").
- Link „Kod źródłowy" prowadzi do `github.com/mikus/eDziennikus`.
- Link „Dziennik zmian" prowadzi do strony GitHub Releases forka
  zamiast wbudowanego pliku `pl-changelog.html`.
- Link „Polityka prywatności" prowadzi do `PRIVACY.md` w repo forka.

### Naprawiono

- Crash `ConcurrentModificationException` w `EdziennikNotification` —
  współbieżne modyfikacje listy akcji w `NotificationCompat.Builder`
  podczas pracy foreground-service powodowały wyjątek głęboko
  w bibliotece NotificationCompat. Wszystkie mutatory powiadomienia
  są teraz zsynchronizowane (`@Synchronized`).
- 449 błędów lintowych blokujących `./gradlew lint` w pipeline'ie CI.
- Crash skryptu `app/git-info.gradle` przy plytkim clonie repozytorium
  (CI domyślnie pobierał tylko jeden commit; teraz workflowy pobierają
  pełną historię z tagami).

### Usunięto (powierzchnie UI)

- Wpis „Twórcy aplikacji" (zależał od endpointu `szkolny.eu/contributors`).
- Wpis „Serwer Discord" (społeczność upstream'u, niedotyczy forka).
- Wpis „Wejdź na stronę aplikacji" (`szkolny.eu` — fork nie ma
  oddzielnej strony internetowej, GitHub Releases jest kanonicznym
  źródłem).
- Przełącznik „Zezwól na rejestrację" w panelu logowania i ustawieniach
  (rejestracja na `szkolny.eu` była warunkiem działania udostępniania
  wydarzeń/notatek, którego fork nie obsługuje).
- Sekcja „Udostępnianie wydarzeń" w ustawieniach agendy
  (cross-user sharing nie działa bez SzkolnyApi).
- Możliwość udostępniania wydarzeń/notatek innym uczniom z klasy
  (UI usunięte, kolumny w Room schemacie usunięte).
- Przycisk „Wyślij raport o błędzie" w `CrashActivity` — brak punktu
  docelowego po stronie serwera. Szczegóły crashu można nadal
  skopiować do schowka i wkleić w zgłoszenie na GitHub Issues.
- Komunikaty „provider niedostępny" oparte o `szkolny.eu`/availability
  endpoint. Błędy łączności z dziennikiem są teraz raportowane przez
  same wywołania HTTP do dziennika, bez wcześniejszego centralnego
  pingu.

[2026.05.0]: https://github.com/mikus/eDziennikus/releases/tag/v2026.05.0
