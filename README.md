<h1 align="center"> Search-Engine. Универсальный поисковый движок  

Дипломная работа для онлайн-школы Skillbox.ru </h1>


<h2> <b>Основные преимущества:</b> </h2>
- Многопоточность. Используется ForkJoinPool <br>
- Универсальность. Программа просто парсит все страницы сайта, работает с html посредством библиотеки JSOUP <br>
- Работа с леммами. Для обработки поисковых запросов и текстов сайта, используется библиотека LuceneMorphology <br>
- Возможность остановить индексацию в любой момент или проиндексировать отдельно взятую страницу <br>
- Удобная поисковая выдача: title страницы, сниппет, слова из поискового запроса подчеркнуты. Все страницы отсортированы по релевантности <br>
- Высокая скорость. Используемая база данных - PostgreSQL + все множественные запросы к БД максимально объединены в транзакции <br>
- Простота в использовании: нужный сайт легко добавляется или убирается в файле настроек <br>
- Управление поисковой выдачей: можно задать лимит выдачи, отступ, можно искать по определенному сайту <br>
- Удобная статистика: сколько сайтов и страниц проиндексировано, информация об ошибках <br>
<br>
<br>
<br>
<h2>Как работает программа:</h2>
  
  1 Запускаем парсинг сайта (сайтов) из списка в многопоточном режиме. Возможна остановка в любой момент или парсинг отдельной страницы. Сохраняем в базу.
<br>
<br>
 <img src="https://github.com/mptimch/Search-Engine/assets/93775557/1c01d2a8-9f34-469b-867f-f4c8d167cdff">
<br>
<br>
  2 Обрабатываем библиотекой LuceneMorphology каждую страницу сайта для получения базовых форм слов (лемм). Все это сохраняется в базу, с учетом количества повторений и в связке со страницами
<br>
<br>
<img src="https://github.com/mptimch/Search-Engine/assets/93775557/c102299b-9ce4-4eb3-bde8-912d4b2f06d5">
<br>
<br>
  3 При получении поискового запроса сверяем леммы из запроса с теми, что у нас в базе. Подбираем список подходящих страниц
  <br>
  4 Сортируем страницы по релевантности, возвращаем подробный ответ на поисковый запрос
  <br>
  <br>
<img src="https://github.com/mptimch/Search-Engine/assets/93775557/f39224f3-4cb2-4105-a219-a12a1f630515">
  <br>
  <br>
5 С поисковой выдачей удобно работать: есть сниппет, title, слова из поискового запроса выделены жирным
  <br>
  <br>
<img src="https://github.com/mptimch/Search-Engine/assets/93775557/db2d87d5-1d59-40af-8feb-c3b9923b76bb">
  <br>
  <br>
6 Множество настроек при поисковом запросе: поиск по сайту или базе, отступ, лимит выдачи
  <br>
  <br>
<img src="https://github.com/mptimch/Search-Engine/assets/93775557/744399be-bcf2-4365-977d-1d8a7f362213">
  <br>
  <br>
7 Возможность проиндексировать отдельную страницу
  <br>
  <br>
<img src="https://github.com/mptimch/Search-Engine/assets/93775557/a495b645-6ad4-46cc-b825-32a30afadf02">
  <br>
  <br>
  <br>
    <br>
  <h4> По всем возникшим вопросам пишите в телеграм <b>@mptimch</b></h4>
