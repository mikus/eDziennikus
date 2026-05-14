# eDziennikus

## O aplikacji

Aktywnie rozwijany fork porzuconej aplikacji Szkolny.eu, obsługujący wyłącznie jeden z popularnych e-dzienników.

### Funkcje aplikacji

- plan lekcji, terminarz, oceny, wiadomości, zadania domowe, uwagi, frekwencja
- wygodne **widgety** na ekran główny
- łatwa komunikacja z nauczycielami — **odbieranie, wyszukiwanie i wysyłanie wiadomości**
- pobieranie **załączników wiadomości i zadań domowych**
- **powiadomienia** o nowych informacjach na telefonie lub na komputerze
- organizacja zadań domowych i sprawdzianów — łatwe oznaczanie jako wykonane
- obliczanie **średniej ocen** ze wszystkich przedmiotów, oceny proponowane i końcowe
- Symulator edycji ocen — obliczanie średniej z przedmiotu po zmianie dowolnych jego ocen
- **dodawanie własnych wydarzeń** i zadań do terminarza
- nowoczesny i intuicyjny interfejs użytkownika
- **obsługa wielu profili** uczniów — jeżeli jesteś Rodzicem, możesz skonfigurować wszystkie swoje konta uczniowskie i łatwo między nimi przełączać
- opcja **automatycznej synchronizacji** z E-dziennikiem
- opcja Ciszy nocnej — nigdy więcej budzących Cię dźwięków z telefonu

### Kompilacja

```
./gradlew assembleDebug
```

Wersję podpisaną do publikacji buduje workflow GitHub Actions na push tagu `v*.*`.

## Współpraca

PRy wprowadzające nowe funkcje lub naprawiające błędy są mile widziane.

Pytania, zgłoszenia błędów oraz propozycje funkcji: [GitHub Issues](https://github.com/mikus/szkolny-android/issues).

## Licencja

eDziennikus publikowany jest na licencji [GNU GPLv3](LICENSE). W szczególności, deweloper:
- Może modyfikować oraz usprawniać kod aplikacji
- Może dystrybuować wersje produkcyjne
- Musi opublikować wszelkie wprowadzone zmiany, tzn. publiczny fork tego repozytorium
- Nie może zmieniać licencji ani copyrightu aplikacji

Dodatkowo:
- Zabronione jest modyfikowanie lub usuwanie kodu odpowiedzialnego za zgodność wersji produkcyjnych z licencją.

- **Wersje skompilowane nie mogą być dystrybuowane za pomocą Google Play oraz żadnej platformy, na której istnieje oficjalna wersja aplikacji**.

**Autorzy aplikacji nie biorą odpowiedzialności za używanie aplikacji, modyfikowanie oraz dystrybuowanie.**

Znaki towarowe zamieszczone w aplikacji oraz tym dokumencie należą do ich prawowitych właścicieli i są używane wyłącznie w celach informacyjnych.

---

Fork zarządzany przez [mikus](https://github.com/mikus); oryginał: Kuba Szczodrzyński i współtwórcy ([szkolny-eu/szkolny-android](https://github.com/szkolny-eu/szkolny-android)).
