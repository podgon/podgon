# Базовый набор категорий (К-А)

Общий фиксированный набор категорий для редизайна (`docs/design.md`, «Категории: общий справочник»; ADR-011
в `docs/architecture.md`). Черновик 2026-11-02, расширяется по мере пожеланий пользователей.

Правила набора:
- **Два уровня:** корневая категория и подкатегория. Рекомендацию можно отнести и к корню, если подходящей
  подкатегории нет, поэтому подкатегорий «Прочее» нет.
- **Названия понятны без родителя** («Исторические книги», а не «История»): в строке ленты и в чипе карточки
  показывается только нижний уровень.
- **Ключ** (`key`) — стабильный латинский идентификатор: из него миграция получает фиксированный UUID, по нему
  клиент и тесты ссылаются на категорию. Ключ не меняется и не переиспользуется; категорию не удаляют, а скрывают
  (`archived`) или сливают с другой.
- Чего нет в наборе, выражается тегами: конкретные страны и города (`#албания`), жанры, бренды, имена.
- Порядок корней и подкатегорий в списках — как в этом файле (поле `sort_order`).
- Системная категория **«Без категории»** (`none`, en: Uncategorized) — только для личных рекомендаций (И28),
  в списках выбора — последней.

Итого: 21 корневая категория и 218 подкатегорий.

## books — Книги / Books
| key | ru | en |
|---|---|---|
| books.fiction | Художественная литература | Fiction |
| books.sf | Фантастика и фэнтези | Science fiction and fantasy |
| books.crime | Детективы и триллеры | Crime and thrillers |
| books.classics | Классика | Classics |
| books.poetry | Поэзия | Poetry |
| books.nonfiction | Нон-фикшн и научпоп | Nonfiction and popular science |
| books.biography | Биографии и мемуары | Biographies and memoirs |
| books.history | Исторические книги | History books |
| books.business | Книги о бизнесе и саморазвитии | Business and self-help books |
| books.psychology | Книги по психологии | Psychology books |
| books.kids | Детские книги | Children's books |
| books.comics | Комиксы и манга | Comics and manga |
| books.audio | Аудиокниги | Audiobooks |

## film — Кино и сериалы / Movies and TV
| key | ru | en |
|---|---|---|
| film.movies | Фильмы | Movies |
| film.series | Сериалы | TV series |
| film.animation | Мультфильмы | Animated films |
| film.anime | Аниме | Anime |
| film.documentary | Документальное кино | Documentaries |
| film.shows | Телешоу | TV shows |
| film.video | Видеоблоги | Video blogs |
| film.cinemas | Кинотеатры | Cinemas |

## music — Музыка и аудио / Music and audio
| key | ru | en |
|---|---|---|
| music.albums | Альбомы | Albums |
| music.artists | Исполнители и группы | Artists and bands |
| music.tracks | Треки | Tracks |
| music.podcasts | Подкасты | Podcasts |

## events — Досуг и мероприятия / Leisure and events
| key | ru | en |
|---|---|---|
| events.concerts | Концерты | Concerts |
| events.festivals | Фестивали | Festivals |
| events.theatre | Театры | Theatres |
| events.opera | Опера и балет | Opera and ballet |
| events.exhibitions | Выставки | Exhibitions |
| events.museums | Музеи | Museums |
| events.standup | Стендап | Stand-up comedy |
| events.quests | Квесты и квизы | Escape rooms and quizzes |
| events.parks | Парки развлечений | Amusement parks |
| events.workshops | Мастер-классы | Workshops |
| events.lectures | Лекции | Lectures |
| events.nightlife | Ночные клубы | Nightclubs |

## food — Рестораны и кафе / Restaurants and cafes
| key | ru | en |
|---|---|---|
| food.coffee | Кофейни | Coffee shops |
| food.bakery | Кондитерские и пекарни | Bakeries and pastry shops |
| food.breakfast | Завтраки и бранчи | Breakfast and brunch |
| food.european | Европейская кухня | European cuisine |
| food.italian | Итальянская кухня | Italian cuisine |
| food.japanese | Японская кухня и суши | Japanese cuisine and sushi |
| food.asian | Азиатская кухня | Asian cuisine |
| food.caucasian | Кавказская кухня | Caucasian cuisine |
| food.russian | Русская кухня | Russian cuisine |
| food.middleeast | Ближневосточная кухня | Middle Eastern cuisine |
| food.world | Другие кухни мира | Other world cuisines |
| food.steak | Стейкхаусы и гриль | Steakhouses and grills |
| food.burgers | Бургерные | Burger places |
| food.pizza | Пиццерии | Pizzerias |
| food.fastfood | Фастфуд и стритфуд | Fast food and street food |
| food.veggie | Вегетарианские и веганские кафе | Vegetarian and vegan cafes |
| food.bars | Бары и пабы | Bars and pubs |
| food.wine | Винные бары | Wine bars |
| food.tea | Чайные | Tea houses |
| food.canteens | Столовые | Canteens |
| food.delivery | Доставка еды | Food delivery |

## cooking — Еда и напитки / Food and drinks
| key | ru | en |
|---|---|---|
| cooking.recipes | Рецепты | Recipes |
| cooking.groceries | Продуктовые магазины | Grocery stores |
| cooking.farm | Фермерские продукты | Farm products |
| cooking.readymeals | Готовая еда и полуфабрикаты | Ready meals |
| cooking.sweets | Сладости и снеки | Sweets and snacks |
| cooking.coffeetea | Кофе и чай | Coffee and tea |
| cooking.wine | Вино | Wine |
| cooking.beer | Пиво | Beer |
| cooking.spirits | Крепкие напитки | Spirits |

## travel — Путешествия / Travel
| key | ru | en |
|---|---|---|
| travel.places | Города и страны | Cities and countries |
| travel.hotels | Отели | Hotels |
| travel.guesthouses | Хостелы и гостевые дома | Hostels and guesthouses |
| travel.rentals | Аренда жилья в поездке | Vacation rentals |
| travel.resorts | Курорты и базы отдыха | Resorts |
| travel.camping | Кемпинги | Campsites |
| travel.sights | Достопримечательности | Sights |
| travel.nature | Природа и национальные парки | Nature and national parks |
| travel.beaches | Пляжи | Beaches |
| travel.hiking | Походы и пешие маршруты | Hiking and trails |
| travel.tours | Экскурсии и гиды | Tours and guides |
| travel.agencies | Турагентства | Travel agencies |
| travel.airlines | Авиакомпании | Airlines |
| travel.trains | Поезда и автобусы | Trains and buses |
| travel.carrental | Аренда автомобилей | Car rental |
| travel.cruises | Круизы | Cruises |

## services — Услуги / Services
| key | ru | en |
|---|---|---|
| services.renovation | Ремонт и отделка | Renovation |
| services.handyman | Мастера на дом | Handymen |
| services.repair | Ремонт техники | Electronics repair |
| services.cleaning | Уборка и клининг | Cleaning services |
| services.laundry | Химчистки и прачечные | Dry cleaning and laundry |
| services.tailor | Ателье и ремонт одежды | Tailors and alterations |
| services.shoerepair | Ремонт обуви | Shoe repair |
| services.moving | Переезды и грузоперевозки | Moving services |
| services.lawyers | Юристы | Lawyers |
| services.accounting | Бухгалтеры и налоги | Accounting and taxes |
| services.notary | Нотариусы | Notaries |
| services.banks | Банки | Banks |
| services.insurance | Страхование | Insurance |
| services.photographers | Фотографы | Photographers |
| services.parties | Организация праздников | Event planning |
| services.couriers | Курьерские службы | Courier services |
| services.telecom | Связь и интернет | Mobile and internet providers |
| services.nannies | Няни | Nannies |

## health — Здоровье / Health
| key | ru | en |
|---|---|---|
| health.clinics | Клиники и медцентры | Clinics and medical centres |
| health.dentists | Стоматологии | Dentists |
| health.doctors | Врачи | Doctors |
| health.labs | Анализы и лаборатории | Medical labs |
| health.pharmacies | Аптеки | Pharmacies |
| health.medicines | Лекарства и витамины | Medicines and vitamins |
| health.therapy | Психологи и психотерапевты | Psychologists and therapists |
| health.massage | Массаж | Massage |
| health.optics | Оптики | Opticians |

## beauty — Красота и уход / Beauty and care
| key | ru | en |
|---|---|---|
| beauty.hair | Парикмахерские | Hair salons |
| beauty.barbers | Барбершопы | Barbershops |
| beauty.nails | Маникюр и педикюр | Nail salons |
| beauty.cosmetology | Косметологи | Cosmetologists |
| beauty.spa | СПА | Spas |
| beauty.sauna | Бани и сауны | Bathhouses and saunas |
| beauty.tattoo | Тату и пирсинг | Tattoo and piercing |
| beauty.cosmetics | Косметика и средства ухода | Cosmetics and skin care |
| beauty.perfume | Парфюмерия | Perfume |

## sport — Спорт и фитнес / Sports and fitness
| key | ru | en |
|---|---|---|
| sport.gyms | Фитнес-клубы | Gyms |
| sport.pools | Бассейны | Swimming pools |
| sport.yoga | Йога и пилатес | Yoga and pilates |
| sport.martial | Единоборства | Martial arts |
| sport.dance | Танцевальные студии | Dance studios |
| sport.climbing | Скалолазание | Climbing |
| sport.running | Бег | Running |
| sport.cycling | Велоспорт | Cycling |
| sport.ski | Лыжи и сноуборд | Skiing and snowboarding |
| sport.team | Командные виды спорта | Team sports |
| sport.coaches | Тренеры | Coaches |
| sport.gear | Спортивная экипировка | Sports gear |
| sport.nutrition | Спортивное питание | Sports nutrition |

## kids — Дети / Kids
| key | ru | en |
|---|---|---|
| kids.kindergartens | Детские сады | Kindergartens |
| kids.clubs | Детские кружки и секции | Kids' clubs and classes |
| kids.toys | Игрушки | Toys |
| kids.clothes | Детская одежда и обувь | Kids' clothing and shoes |
| kids.baby | Товары для малышей | Baby products |
| kids.parties | Детские праздники и аниматоры | Kids' parties and entertainers |
| kids.fun | Детские развлечения и площадки | Kids' entertainment and playgrounds |
| kids.camps | Детские лагеря | Kids' camps |

## education — Образование / Education
| key | ru | en |
|---|---|---|
| education.online | Онлайн-курсы | Online courses |
| education.languages | Языковые курсы | Language courses |
| education.tutors | Репетиторы | Tutors |
| education.schools | Школы | Schools |
| education.universities | Вузы | Universities |
| education.apps | Обучающие приложения | Learning apps |
| education.driving | Автошколы | Driving schools |

## shopping — Покупки / Shopping
| key | ru | en |
|---|---|---|
| shopping.clothes | Одежда | Clothing |
| shopping.shoes | Обувь | Shoes |
| shopping.accessories | Аксессуары и сумки | Accessories and bags |
| shopping.jewelry | Украшения и часы | Jewelry and watches |
| shopping.furniture | Мебель | Furniture |
| shopping.home | Товары для дома | Home goods |
| shopping.kitchen | Посуда и кухонные принадлежности | Cookware and kitchenware |
| shopping.gifts | Подарки | Gifts |
| shopping.flowers | Цветы | Flowers |
| shopping.stationery | Канцелярия | Stationery |
| shopping.bookstores | Книжные магазины | Bookstores |
| shopping.marketplaces | Интернет-магазины и маркетплейсы | Online stores and marketplaces |
| shopping.secondhand | Секонд-хенд и винтаж | Second-hand and vintage |
| shopping.crafts | Товары для хобби и рукоделия | Hobby and craft supplies |
| shopping.tools | Инструменты | Tools |
| shopping.garden | Товары для сада и огорода | Garden supplies |
| shopping.instruments | Музыкальные инструменты | Musical instruments |

## tech — Техника и электроника / Tech and electronics
| key | ru | en |
|---|---|---|
| tech.phones | Смартфоны | Smartphones |
| tech.computers | Ноутбуки и компьютеры | Laptops and computers |
| tech.tablets | Планшеты и электронные книги | Tablets and e-readers |
| tech.audio | Наушники и аудиотехника | Headphones and audio |
| tech.tv | Телевизоры | TVs |
| tech.cameras | Фото- и видеотехника | Cameras |
| tech.smarthome | Умный дом | Smart home |
| tech.appliances | Крупная бытовая техника | Major appliances |
| tech.smallappliances | Мелкая бытовая техника | Small appliances |
| tech.consoles | Игровые консоли | Game consoles |
| tech.gadgets | Гаджеты и аксессуары | Gadgets and accessories |
| tech.apps | Мобильные приложения | Mobile apps |
| tech.software | Программы и онлайн-сервисы | Software and online services |

## games — Игры / Games
| key | ru | en |
|---|---|---|
| games.pc | Компьютерные игры | PC games |
| games.console | Консольные игры | Console games |
| games.mobile | Мобильные игры | Mobile games |
| games.board | Настольные игры | Board games |

## realestate — Недвижимость / Real estate
| key | ru | en |
|---|---|---|
| realestate.developers | Застройщики и новостройки | Developers and new buildings |
| realestate.complexes | Жилые комплексы | Residential complexes |
| realestate.management | Управляющие компании | Property management |
| realestate.agents | Риелторы | Real estate agents |
| realestate.rent | Аренда квартир | Apartment rentals |

## auto — Авто и транспорт / Cars and transport
| key | ru | en |
|---|---|---|
| auto.cars | Автомобили | Cars |
| auto.service | Автосервисы | Car repair |
| auto.tires | Шиномонтаж | Tire service |
| auto.wash | Автомойки | Car wash |
| auto.dealers | Автосалоны | Car dealers |
| auto.parts | Запчасти и автотовары | Car parts and accessories |
| auto.carsharing | Каршеринг | Car sharing |
| auto.taxi | Такси | Taxi |
| auto.transit | Общественный транспорт | Public transport |
| auto.bikes | Велосипеды и самокаты | Bikes and scooters |
| auto.parking | Парковки | Parking |
| auto.fuel | АЗС | Gas stations |

## pets — Животные / Pets
| key | ru | en |
|---|---|---|
| pets.vets | Ветеринарные клиники | Veterinary clinics |
| pets.shops | Зоомагазины | Pet stores |
| pets.food | Корма | Pet food |
| pets.grooming | Груминг | Grooming |
| pets.boarding | Передержка и зоогостиницы | Pet boarding |
| pets.training | Кинологи и дрессировка | Dog training |
| pets.shelters | Приюты | Animal shelters |

## hobbies — Хобби и творчество / Hobbies and crafts
| key | ru | en |
|---|---|---|
| hobbies.needlework | Рукоделие | Needlework |
| hobbies.drawing | Рисование | Drawing and painting |
| hobbies.photo | Фотография | Photography |
| hobbies.music | Игра на музыкальных инструментах | Playing music |
| hobbies.gardening | Садоводство | Gardening |
| hobbies.fishing | Рыбалка и охота | Fishing and hunting |
| hobbies.collecting | Коллекционирование | Collecting |
| hobbies.models | Моделирование | Model building |
| hobbies.diy | Мастерские и DIY | Makerspaces and DIY |

## work — Работа и бизнес / Work and business
| key | ru | en |
|---|---|---|
| work.employers | Работодатели | Employers |
| work.coworking | Коворкинги | Coworking spaces |
| work.b2b | Сервисы для бизнеса | Business services |
| work.conferences | Конференции | Conferences |
