# Ultimate Natural Disasters (UND) — v0.3.0

**Hedef:** Minecraft Java 1.20.1 · Forge 47.x · Java 17

Bu ZIP, derlenebilir olması hedeflenen bir **kaynak projedir**; henüz doğrulanmış bir `.jar` değildir. Bu çalışma ortamında Gradle/Forge bağımlılıkları bulunmadığından derleme ve oyun içi test yapılamadı. Oyunda test edilmeden "tamamen çalışıyor" denmemelidir.

## Afetler

| Komut kimliği | Afet | Bu sürümdeki uygulama |
|---|---|---|
| `quake_fault` | Fay hattı depremi | Yer altında fay izi parçacıkları, yüzey şok halkası, sarsıntı sesi |
| `quake_surface` | Zemin depremi | Yüzeyden dışarı yayılan şok halkası ve toz |
| `tsunami` | Tsunami | İleri hareket eden su/sıçrama parçacığı duvarı, yakındaki varlıklara itme |
| `tornado` | Hortum | Hareket eden dönen huni, zemin toz halkası, canlıları girdaba çekip yükseltme ve sınırlı sayıda gerçek bloğu FallingBlockEntity olarak havalandırıp döndürme |
| `landslide` | Heyelan | Yamaç çevresinde kaya/toprak parçacıkları ve sarsıntı |
| `storm` | Fırtına | Yağışlı hava, bulut parçacıkları |
| `superstorm` | Süper fırtına | Gök gürültülü hava, yoğun parçacıklar ve yıldırım efektleri |
| `volcano` | Yanardağ | Lav parçacıkları ve kül/duman sütunu |
| `blizzard` | Kar fırtınası | Yoğun kar parçacıkları ve yağışlı hava |
| `hail` | Dolu | Yoğun buz parçacıkları ve çarpma sesleri |
| `flood` | Sel | Yükseliyor gibi görünen su/baloncuk halkası, varlıklara itme |
| `wildfire` | Orman yangını | Alev ve duman parçacıkları |
| `meteor` | Meteor yağmuru | Gökyüzünden inen ateşli parçacık kümeleri ve patlama sesleri |

## Komutlar

- `/und help` — komut listesi
- `/und start <afet> [1-5]` — afet başlat
- `/und random` — rastgele afet ve şiddet
- `/und auto on` — yaklaşık 2-4 dakikada bir otomatik afet başlat
- `/und auto off` — otomatik sistemi kapat
- `/und status` — yakındaki aktif afet sayısı
- `/und stop` — yakındaki afetleri durdur

Örnekler:

```text
/und start quake_fault 5
/und start quake_surface 4
/und start tsunami 5
/und start tornado 4
/und start superstorm 5
/und start volcano 5
/und random
```

## Derleme

Proje kökünde Gradle 8.x ve Java 17 ile:

```bash
gradle build
```

Oluşan JAR normalde `build/libs/` altında olur. JAR'ı Minecraft `mods` klasörüne koymadan önce aynı Forge sürümünde test et.

## Hortum geliştirmesi (v0.3.0)

- Hortum artık yalnızca parçacık değildir: yüzeyden sınırlı sayıda normal bloğu söker ve gerçek `FallingBlockEntity` olarak havaya kaldırır.
- Bloklar girdap çevresinde teğetsel hareket ve yükselme hızı alır; doğal düşüş fiziğiyle tekrar yere iner.
- Yakındaki canlılar ve oyuncular merkeze doğru çekilir, döndürülür ve yukarı taşınır.
- Huni 30 blok yüksekliğe kadar genişleyen bir şekle sahiptir; zeminde dönen toz halkası ve duman katmanları bulunur.
- Şiddete göre blok kaldırma sınırı: 15–43 blok. Her 8 tick'te sınırlı tarama yapılır.
- Sandık/fırın gibi blok varlıkları (envanter içeren bloklar), sıvılar, yatak kaya, bariyer, portal ve komut/structure blokları korunur.
- `/und stop` hortumu durdurur; o ana kadar havalanmış bloklar normal düşüşleriyle yere iner.

## Bilinen sınırlar / sonraki geliştirme

- Tsunami ve sel şu anda gerçek su bloklarını doldurmaz; görsel dalga/sel simülasyonudur.
- Heyelan hâlâ görsel kaya/toprak parçacıkları kullanır; gerçek arazi taşıması sonraki geliştirmedir. Hortum blok kaldırma özelliği v0.3.0 ile eklendi.
- Volkan lav bloklarını araziye yaymaz; püskürme görseldir.
- Kamera sarsıntısı için istemci tarafı özel ağ paketi ve render katmanı gerekir; bu sürümde yoktur.
- Otomatik afet sistemi açılıp kapatılabilir; sıklık şu an sabit olarak yaklaşık 2-4 dakikadır. Yapılandırma ekranı, özel ses/texture ve kapsamlı çok oyunculu test sonraki sürümlere kalır.
- Aynı dünyada en fazla dört aktif afet tutulur; parçacık üretimi sınırlıdır.
