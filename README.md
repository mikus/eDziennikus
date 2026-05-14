# eDziennikus

## O aplikacji

**eDziennikus** to osobisty fork porzuconej aplikacji [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android), zawężony do obsługi wyłącznie jednego z popularnych e-dzienników. Powstał jako alternatywa po wygaśnięciu rozwoju oryginału — utrzymywany prywatnie, dystrybuowany wyłącznie jako sideload (GitHub Releases), bez Firebase, bez integracji z infrastrukturą Szkolny.eu.

### Funkcje aplikacji

- plan lekcji, terminarz, oceny, wiadomości, zadania domowe, uwagi, frekwencja
- wygodne **widgety** na ekran główny
- łatwa komunikacja z nauczycielami — **odbieranie, wyszukiwanie i wysyłanie wiadomości**
- pobieranie **załączników wiadomości i zadań domowych**
- lokalne **powiadomienia** o nowych informacjach
- organizacja zadań domowych i sprawdzianów — łatwe oznaczanie jako wykonane
- obliczanie **średniej ocen** ze wszystkich przedmiotów, oceny proponowane i końcowe
- Symulator edycji ocen — obliczanie średniej z przedmiotu po zmianie dowolnych jego ocen
- **dodawanie własnych wydarzeń** i zadań do terminarza
- nowoczesny i intuicyjny interfejs użytkownika
- **obsługa wielu profili** uczniów Librusa — jeżeli jesteś Rodzicem, możesz skonfigurować wszystkie swoje konta uczniowskie i łatwo między nimi przełączać
- opcja **automatycznej synchronizacji** z e-dziennikiem (oparta o WorkManager — odświeżanie co ~15-30 min zgodnie z polityką oszczędzania baterii Androida)
- opcja Ciszy nocnej — nigdy więcej budzących Cię dźwięków z telefonu

### Czym ten fork różni się od oryginału

W stosunku do upstreamu (`szkolny-eu/szkolny-android`):

- **Obsługuje wyłącznie jednego providera** (wybranego przez właściciela forka). Usunięto kod pozostałych providerów dziedziczonych z upstreamu oraz odpowiadające im fragmenty UI, zasoby i przepływy logowania.
- **Bez Firebase / FCM / Crashlytics.** Brak powiadomień push w czasie rzeczywistym — aplikacja korzysta wyłącznie z lokalnego pollingu przez WorkManager. Brak telemetrii.
- **Bez integracji z backendem Szkolny.eu.** Aplikacja jest całkowicie niezależna od centralnej infrastruktury Szkolny.eu — sprawdzanie wersji, dystrybucja Web Push, statystyki użytkowania itp. zostały usunięte.
- **Pojedynczy product flavor.** Trzy flavors upstreamu (`unofficial`, `official`, `play`) zostały zwinięte do jednego — fork ma jedną postać, dystrybuowaną tylko przez GitHub Releases.
- **Czysta baza danych przy pierwszym uruchomieniu.** Aplikacja używa nowego `applicationId` (`eu.mikus.edziennik`), więc Android traktuje ją jako odrębną aplikację — nie odczytuje ani nie modyfikuje danych oryginalnego Szkolny.eu. Schema Room zresetowano do v1 bez historii migracji.
- **Nowoczesny toolchain.** Gradle 9.5.0, AGP 8.13.2, Kotlin 2.3.20, JDK 17.
- **CI/CD od zera.** Workflow GitHub Actions: build na każdy push/PR, podpisany release przy push tagu `v*`.

Kod pozostawiono w architekturze zgodnej z upstreamem, by ułatwić ewentualne portowanie poprawek między forkiem a oryginałem.

### Kompilacja

```
./gradlew assembleDebug
```

Wersję podpisaną do publikacji buduje workflow GitHub Actions na push tagu `v*.*`.

## Współpraca

PRy wprowadzające nowe funkcje lub naprawiające błędy są mile widziane.

Pytania, zgłoszenia błędów oraz propozycje funkcji: [GitHub Issues](https://github.com/mikus/eDziennikus/issues).

## Licencja

eDziennikus publikowany jest na licencji [GNU GPLv3](LICENSE). W szczególności, deweloper:
- Może modyfikować oraz usprawniać kod aplikacji
- Może dystrybuować wersje produkcyjne
- Musi opublikować wszelkie wprowadzone zmiany, tzn. publiczny fork tego repozytorium
- Nie może zmieniać licencji ani copyrightu aplikacji

Dodatkowo:
- Zabronione jest modyfikowanie lub usuwanie kodu odpowiedzialnego za zgodność wersji produkcyjnych z licencją.

- **Wersje skompilowane nie mogą być dystrybuowane za pomocą Google Play oraz żadnej innej platformy poza oryginalnym repozytorium**.

**Autorzy aplikacji nie biorą odpowiedzialności za używanie aplikacji, modyfikowanie oraz dystrybuowanie.**

Znaki towarowe zamieszczone w aplikacji oraz tym dokumencie należą do ich prawowitych właścicieli i są używane wyłącznie w celach informacyjnych.

## Podziękowania

Cała zasługa za stworzenie aplikacji należy do zespołu **Szkolny.eu** ([szkolny-eu/szkolny-android](https://github.com/szkolny-eu/szkolny-android)). Ten fork jest tylko zachowaniem i zawężeniem ich pracy, nie nowym dziełem.

**Główni autorzy oryginału:**

- **Kuba Szczodrzyński** ([@kuba2k2](https://github.com/kuba2k2), organizacja [szkolny-eu](https://github.com/szkolny-eu)) — twórca i główny maintainer Szkolny.eu; autor architektury aplikacji, warstwy API providerów, natywnego kodu signing/crypto, i większości kodu UI.
- **Kacper Ziubryniewicz** — drugi główny kontrybutor; znaczący wkład w warstwę API, sync engine, widgety, oraz refactoring kodu.

**Pozostali kontrybutorzy** (alfabetycznie): Adam Kasprzycki, Adam Rurański, Antoni Czaplicki, B.O.S.S, doteq, franek, KrystianQur, koliwbr, Marcin Kowalicki, Mateusz Idziejczak, Mateusz T, Oskar, Patryk, Sylwester Zinkiewicz, Tomasz F, arin.

Pełna lista commitów oraz autorów dostępna w historii git oryginalnego repozytorium ([szkolny-eu/szkolny-android](https://github.com/szkolny-eu/szkolny-android/graphs/contributors)). Nagłówki copyright w plikach źródłowych zostały zachowane bez zmian — wszystkie zachowują oryginalne atrybucje autora i daty.

---

Fork zarządzany przez [mikus](https://github.com/mikus). Oryginał: zespół Szkolny.eu ([szkolny-eu/szkolny-android](https://github.com/szkolny-eu/szkolny-android)).
