from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager
import time
import re
from datetime import datetime, timedelta

import requests
import os

class VenueCrawler:
    def __init__(self):
        options = webdriver.ChromeOptions()
        options.add_argument("--headless")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--ignore-certificate-errors")
        options.add_argument("user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        options.add_argument("--accept-insecure-certs") # SSL 에러 무시 강화
        
        self.driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=options)
        self.wait = WebDriverWait(self.driver, 10)
        
        # Kakao API Key for Geocoding
        self.KAKAO_API_KEY = os.getenv("KAKAO_REST_API_KEY")

    def close(self):
        self.driver.quit()

    def get_geo_location(self, query):
        if not self.KAKAO_API_KEY:
            return None
            
        url = "https://dapi.kakao.com/v2/local/search/keyword.json"
        headers = {"Authorization": f"KakaoAK {self.KAKAO_API_KEY}"}
        params = {"query": query}
        
        try:
            resp = requests.get(url, headers=headers, params=params)
            data = resp.json()
            if data.get('documents'):
                # 첫 번째 결과 사용
                place = data['documents'][0]
                return {
                    "lat": float(place['y']),
                    "lng": float(place['x']),
                    "place_name": place['place_name'],
                    "address": place.get('road_address_name') or place.get('address_name')
                }
        except Exception as e:
            print(f"Geocoding error for {query}: {e}")
            
        return None

    def geocode_place(self, query):
        """
        Helper method to get lat, lng tuple from get_geo_location.
        Returns (0.0, 0.0) if not found.
        """
        geo = self.get_geo_location(query)
        if geo:
            return geo['lat'], geo['lng']
        return 0.0, 0.0

    def crawl_incheon_tour(self):
        results = []
        # List page: https://itour.incheon.go.kr/event/festival/list.do
        url = "https://itour.incheon.go.kr/event/festival/list.do"
        print(f"Crawling Incheon Tour: {url}")
        
        try:
            self.driver.get(url)
            time.sleep(3)
            
            # Items: .gallery_list ul li
            items = self.driver.find_elements(By.CSS_SELECTOR, ".gallery_list ul li")
            
            print(f"Found {len(items)} items in Incheon Tour List")
            
            for item in items:
                try:
                    # Title
                    title = item.find_element(By.CSS_SELECTOR, ".subject").text.strip()
                    if not title:
                        continue
                        
                    # Link
                    link = item.find_element(By.TAG_NAME, "a").get_attribute("href")
                    
                    # Image
                    try:
                        img_src = item.find_element(By.CSS_SELECTOR, ".thumb img").get_attribute("src")
                    except:
                        img_src = ""
                    
                    # Date & Location
                    # .date -> 2025.01.01 ~ 2025.12.31
                    # .place -> Location
                    date_text = ""
                    location = "인천"
                    
                    try:
                        infos = item.find_elements(By.CSS_SELECTOR, ".info li")
                        for info in infos:
                            txt = info.text
                            if "기간" in txt:
                                date_text = txt.replace("기간", "").strip()
                            elif "장소" in txt:
                                location = txt.replace("장소", "").strip()
                    except:
                        pass
                    
                    # Date parsing
                    start_date, end_date = self.parse_date_range(date_text)
                    
                    # 날짜 필터링 (오늘 이후 이벤트만)
                    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                    if end_date and end_date < today:
                        continue
                    
                    # 좌표 변환
                    geo = self.get_geo_location(location)
                    if not geo:
                        geo = self.get_geo_location(title) # Try title
                    if not geo:
                        geo = self.get_geo_location("인천시청") # Fallback
                    
                    results.append({
                        "title": title,
                        "category": "축제",
                        "date": date_text,
                        "start_date": start_date,
                        "end_date": end_date,
                        "location": location,
                        "lat": geo['lat'] if geo else 0.0,
                        "lng": geo['lng'] if geo else 0.0,
                        "description": "인천투어 공식 행사",
                        "image": img_src,
                        "source": "IncheonTour",
                        "link": link
                    })
                except Exception as e:
                    # print(f"Error parsing item: {e}")
                    continue
                    
        except Exception as e:
            print(f"Error crawling Incheon Tour: {e}")
            
        return results

    def crawl_yeonsu_foundation(self):
        results = []
        # 연수문화재단 (도메인 변경: yscf.or.kr -> ysfac.or.kr)
        # 공지사항 직접 이동
        url = "https://www.ysfac.or.kr/user/board/list.php?board_code=notice"
        print(f"Crawling Yeonsu Foundation: {url}")
        
        try:
            self.driver.get(url)
            time.sleep(3)
            
            rows = self.driver.find_elements(By.CSS_SELECTOR, "tbody tr")
            print(f"Found {len(rows)} rows in Yeonsu Notice")
            
            # 1. Collect links first
            links_data = []
            for row in rows:
                try:
                    try:
                        link_elem = row.find_element(By.CSS_SELECTOR, "td.title a")
                    except:
                        link_elem = row.find_element(By.TAG_NAME, "a")
                        
                    title = link_elem.text.strip()
                    link = link_elem.get_attribute("href")
                    
                    # Date extraction
                    date_text = datetime.now().strftime("%Y%m%d")
                    cols = row.find_elements(By.TAG_NAME, "td")
                    for col in cols:
                        txt = col.text.strip()
                        if re.search(r'\d{4}[\.-]\d{2}[\.-]\d{2}', txt):
                            date_text = txt.replace("-", "").replace(".", "")
                            break
                    
                    links_data.append({
                        "title": title,
                        "link": link,
                        "date_text": date_text
                    })
                except:
                    continue
            
            # 2. Visit detail pages
            for item in links_data:
                title = item['title']
                
                # Keyword filter
                if not any(keyword in title for keyword in ["공연", "전시", "축제", "행사", "모집", "개최", "페스티벌"]):
                    continue
                    
                # Date parsing
                start_date, end_date = self.parse_date_range(item['date_text'])
                today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                if end_date and end_date < today:
                    continue

                # Visit detail page for location
                try:
                    self.driver.get(item['link'])
                    time.sleep(1)
                    
                    place = "연수문화재단" # Default
                    
                    # Try to find place
                    try:
                        # Look for th containing '장소' or '위치'
                        xpath = "//th[contains(text(), '장소') or contains(text(), '위치')]/following-sibling::td"
                        place_elem = self.driver.find_element(By.XPATH, xpath)
                        place_text = place_elem.text.strip()
                        if place_text:
                            place = place_text
                    except:
                        # Try body text regex
                        try:
                            body_text = self.driver.find_element(By.TAG_NAME, "body").text
                            match = re.search(r"(장\s*소|위\s*치)\s*[:]\s*(.+)", body_text)
                            if match:
                                place = match.group(2).strip().split("\n")[0]
                        except:
                            pass
                    
                    # Geocoding
                    lat, lng = self.geocode_place(place)
                    if lat == 0.0:
                        # Fallback to foundation office if specific place not found
                        lat, lng = self.geocode_place("연수문화재단")
                        
                    results.append({
                        "title": title,
                        "category": "문화/예술",
                        "date": item['date_text'],
                        "start_date": start_date,
                        "end_date": end_date,
                        "location": place,
                        "lat": lat,
                        "lng": lng,
                        "description": "연수문화재단 공지사항",
                        "image": "",
                        "source": "YeonsuFoundation",
                        "link": item['link']
                    })
                    print(f"  -> {title} @ {place} ({lat}, {lng})")
                    
                except Exception as e:
                    print(f"Error visiting {item['link']}: {e}")
                    
        except Exception as e:
            print(f"Error crawling Yeonsu Foundation: {e}")
            
        return results

    def crawl_triple_street(self):
        results = []
        url = "https://www.triplestreet.co.kr/"
        print(f"Crawling Triple Street: {url}")
        
        try:
            self.driver.get(url)
            time.sleep(5)
            
            # 메인 페이지 하단 '공지사항' 섹션
            # div.cc_box_inner.newsNnoti ul li a
            items = self.driver.find_elements(By.CSS_SELECTOR, ".cc_box_inner.newsNnoti ul li a")
            
            print(f"Found {len(items)} items in Triple Street Notice")
            
            for item in items:
                try:
                    title = item.find_element(By.CSS_SELECTOR, ".subject").text.strip()
                    date_text = item.find_element(By.CSS_SELECTOR, ".date").text.strip()
                    link = item.get_attribute("href")
                    
                    # 날짜 포맷 변환 (YYYY.MM.DD -> YYYYMMDD)
                    try:
                        date_obj = datetime.strptime(date_text, "%Y.%m.%d")
                        formatted_date = date_obj.strftime("%Y%m%d")
                    except:
                        formatted_date = datetime.now().strftime("%Y%m%d")

                    # 날짜 필터링 (오늘 이후 이벤트만)
                    today_str = datetime.now().strftime("%Y%m%d")
                    if formatted_date < today_str:
                        continue

                    # 좌표 변환 (트리플스트리트)
                    geo = self.get_geo_location("송도 트리플스트리트")

                    results.append({
                        "title": title,
                        "category": "쇼핑/이벤트",
                        "date": formatted_date,
                        "start_date": date_obj,
                        "end_date": date_obj,
                        "location": "트리플스트리트",
                        "lat": geo['lat'] if geo else 0.0,
                        "lng": geo['lng'] if geo else 0.0,
                        "description": "트리플스트리트 공지사항",
                        "image": "",
                        "source": "TripleStreet",
                        "link": link
                    })
                except Exception as e:
                    # print(f"Error parsing item: {e}")
                    pass
                    
        except Exception as e:
            print(f"Error crawling Triple Street: {e}")
            
        return results

    def crawl_incheon_cultural_foundation(self):
        """
        4. 인천문화재단 (Incheon Cultural Foundation)
        URL: https://www.ifac.or.kr/culturalInfo/cuturalEvents/performanceSrch/list.do?key=m2501152621396
        """
        url = "https://www.ifac.or.kr/culturalInfo/cuturalEvents/performanceSrch/list.do?key=m2501152621396"
        print(f"Crawling Incheon Cultural Foundation: {url}")
        results = []
        
        try:
            self.driver.get(url)
            time.sleep(3)
            
            # Items: .thumbList ul li
            items = self.driver.find_elements(By.CSS_SELECTOR, ".thumbList ul li")
            print(f"Found {len(items)} items in Incheon Cultural Foundation")
            
            for item in items:
                try:
                    # Title
                    title = item.find_element(By.CSS_SELECTOR, ".text .title").text.strip()
                    
                    # Link (onclick="goView('13382');")
                    # Construct: https://www.ifac.or.kr/culturalInfo/cuturalEvents/performanceSrch/view.do?key=m2501152621396&seq=13382
                    try:
                        onclick = item.find_element(By.TAG_NAME, "a").get_attribute("onclick")
                        seq_match = re.search(r"goView\('(\d+)'\)", onclick)
                        if seq_match:
                            seq = seq_match.group(1)
                            link = f"https://www.ifac.or.kr/culturalInfo/cuturalEvents/performanceSrch/view.do?key=m2501152621396&seq={seq}"
                        else:
                            link = url
                    except:
                        link = url

                    # Image
                    try:
                        img_src = item.find_element(By.CSS_SELECTOR, ".img img").get_attribute("src")
                        if "no_image" in img_src:
                            img_src = ""
                    except:
                        img_src = ""

                    # Date & Place from .info li
                    # Format: 
                    # <li><span>모임기간</span>2025-01-01 ~ 2025-01-31</li>
                    # <li><span>일시</span>...</li>
                    # <li><span>주최</span>...</li>
                    # <li><span>장소</span>트라이보울</li>
                    
                    date_text = ""
                    place = "인천문화재단"
                    
                    infos = item.find_elements(By.CSS_SELECTOR, ".info li")
                    for info in infos:
                        text = info.text
                        if "모임기간" in text or "일시" in text:
                            date_text = text.replace("모임기간", "").replace("일시", "").strip()
                        elif "장소" in text:
                            place = text.replace("장소", "").strip()

                    if not title:
                        continue

                    # Date parsing
                    start_date, end_date = self.parse_date_range(date_text)
                    
                    # Filter by date (today onwards - future events only)
                    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                    if end_date and end_date < today:
                        continue

                    # Geocoding
                    lat, lng = self.geocode_place(place)
                    if lat == 0.0:
                         lat, lng = self.geocode_place("인천문화재단")

                    results.append({
                        "title": title,
                        "category": "문화/예술",
                        "link": link,
                        "image": img_src,
                        "date": date_text,
                        "start_date": start_date,
                        "end_date": end_date,
                        "location": place,
                        "lat": lat,
                        "lng": lng,
                        "description": "인천문화재단 행사",
                        "source": "인천문화재단"
                    })
                except Exception as e:
                    # print(f"Error parsing item: {e}")
                    continue
                    
        except Exception as e:
            print(f"Error crawling Incheon Cultural Foundation: {e}")
            
        return results

    def crawl_incheon_arts_center(self):
        """
        5. 인천문화예술회관 (Incheon Culture & Arts Center)
        URL: https://www.incheon.go.kr/art/ART010101
        """
        print("Crawling Incheon Arts Center...")
        results = []
        
        # Current month and Next month
        today = datetime.now()
        next_month = today.replace(day=28) + timedelta(days=4) # Go to next month
        
        target_dates = [
            today.strftime("%Y-%m"),
            next_month.strftime("%Y-%m")
        ]
        
        base_url = "https://www.incheon.go.kr/art/ART010101"
        
        for yyyy_mm in target_dates:
            year, month = yyyy_mm.split("-")
            # Construct URL with query params if possible, or just visit base and click?
            # Looking at typical gov sites, they use ?year=2025&month=01
            url = f"{base_url}?year={year}&month={month}"
            print(f"  -> Visiting {yyyy_mm}: {url}")
            
            try:
                self.driver.get(url)
                time.sleep(3)
                
                items = self.driver.find_elements(By.CSS_SELECTOR, ".board-data-list table tbody tr")
                print(f"  -> Found {len(items)} items for {yyyy_mm}")
                
                for item in items:
                    try:
                        # Skip empty rows or "No data" rows
                        if "데이터가 없습니다" in item.text:
                            continue

                        # Title & Link
                        title_elem = item.find_element(By.CSS_SELECTOR, "td.al a")
                        title = title_elem.text.strip()
                        link = title_elem.get_attribute("href")
                        
                        # Date
                        date_text = item.find_element(By.CSS_SELECTOR, "td:nth-child(2)").text.strip()
                        
                        # Location (Specific hall)
                        place = item.find_element(By.CSS_SELECTOR, "td:nth-child(3)").text.strip()
                        
                        if not title:
                            continue

                        # Date parsing
                        start_date, end_date = self.parse_date_range(date_text)
                        
                        # Filter by date (today onwards - future events only)
                        # For next month events, fetching everything is fine.
                        # But we should filter out past events from current month.
                        today_dt = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                        if end_date and end_date < today_dt:
                            continue

                        # Geocoding
                        lat, lng = self.geocode_place("인천문화예술회관")
                        
                        results.append({
                            "title": title,
                            "category": "공연/전시",
                            "link": link,
                            "image": "", # No image in list view
                            "date": date_text,
                            "start_date": start_date,
                            "end_date": end_date,
                            "location": f"인천문화예술회관 {place}",
                            "lat": lat,
                            "lng": lng,
                            "description": "인천문화예술회관 공연/전시",
                            "source": "인천문화예술회관"
                        })
                    except Exception as e:
                        # print(f"Error parsing item: {e}")
                        continue
                        
            except Exception as e:
                print(f"Error crawling Incheon Arts Center ({yyyy_mm}): {e}")
            
        return results

    def crawl_songdo_convensia(self):
        """
        5. 송도컨벤시아 (Songdo Convensia)
        공식 사이트가 다운되어 있어 네이버 블로그 검색으로 대체
        """
        print("Crawling Songdo Convensia (via Naver search)...")
        results = []
        
        try:
            # 네이버 블로그 검색으로 송도컨벤시아 행사 검색
            url = "https://openapi.naver.com/v1/search/blog.json"
            headers = {
                "X-Naver-Client-Id": os.environ.get("NAVER_CLIENT_ID", ""),
                "X-Naver-Client-Secret": os.environ.get("NAVER_CLIENT_SECRET", "")
            }
            
            if not headers["X-Naver-Client-Id"]:
                print("  -> Naver API keys not found, skipping.")
                return []
            
            params = {"query": "송도컨벤시아 전시 행사 2025", "display": 10, "sort": "date"}
            resp = requests.get(url, headers=headers, params=params, timeout=10)
            data = resp.json()
            items = data.get('items', [])
            
            for item in items:
                title = re.sub('<.+?>', '', item['title'])
                desc = re.sub('<.+?>', '', item['description'])
                
                # 송도컨벤시아 관련 글만 필터링
                if "송도컨벤시아" not in title and "송도컨벤시아" not in desc:
                    continue
                
                # 행사 키워드 확인
                event_keywords = ["전시", "박람회", "페어", "컨퍼런스", "엑스포", "행사"]
                if not any(kw in title or kw in desc for kw in event_keywords):
                    continue
                
                # 송도컨벤시아 좌표 (고정값)
                results.append({
                    "title": title,
                    "category": "전시",
                    "date": item.get('postdate', ''),
                    "start_date": None,
                    "end_date": None,
                    "location": "송도컨벤시아",
                    "lat": 37.3933,
                    "lng": 126.6344,
                    "description": desc[:200],
                    "image": "",
                    "source": "NaverBlog-Convensia",
                    "link": item['link']
                })
            
            print(f"Found {len(results)} items via Naver search for Songdo Convensia")
        except Exception as e:
            print(f"Songdo Convensia crawling failed: {e}")
        
        return results

    def crawl_songdo_community(self):
        """
        6. 송도 주민자치센터 (Songdo Community Centers)
        URLs:
        - Songdo 1-dong: https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx=39
        - Songdo 2-dong: https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx=53
        - Songdo 3-dong: https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx=64
        - Songdo 4-dong: https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx=70
        - Songdo 5-dong: https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx=77
        """
        print("Crawling Songdo Community Centers...")
        results = []
        
        dong_ids = {
            "39": "송도1동",
            "53": "송도2동",
            "64": "송도3동",
            "70": "송도4동",
            "77": "송도5동"
        }
        
        for team_idx, dong_name in dong_ids.items():
            url = f"https://www.yeonsu.go.kr/edu/sub/apply.asp?team_idx={team_idx}"
            print(f"Crawling {dong_name}: {url}")
            
            try:
                self.driver.get(url)
                time.sleep(3)
                
                items = self.driver.find_elements(By.CSS_SELECTOR, ".donglec_list ul li")
                print(f"Found {len(items)} items in {dong_name}")
                
                for item in items:
                    try:
                        # Skip if closed? No, include them but maybe mark as closed?
                        # For now, just extract all.
                        
                        # Title
                        title = item.find_element(By.CSS_SELECTOR, "dt p").text.strip()
                        
                        # Link
                        link = item.find_element(By.TAG_NAME, "a").get_attribute("href")
                        
                        # Image (No image in list, use default or empty)
                        image = ""
                        
                        # Date (Education Period)
                        # .time .a text ": 10.1~12.17"
                        date_text = item.find_element(By.CSS_SELECTOR, ".time .a").text.replace(":", "").strip()
                        
                        # Place
                        place = item.find_element(By.CSS_SELECTOR, ".place .a").text.replace(":", "").strip()
                        if not place:
                            place = f"{dong_name} 주민자치센터"

                        if not title:
                            continue

                        # Date parsing
                        # Try to extract year from application period first
                        app_period = item.find_element(By.CSS_SELECTOR, ".period .a").text.strip()
                        year_match = re.search(r"(\d{4})", app_period)
                        year = year_match.group(1) if year_match else str(datetime.now().year)
                        
                        # Handle "MM.DD~MM.DD" format
                        # Example: "10.1~12.17" -> "2025.10.1 ~ 2025.12.17"
                        if "~" in date_text:
                            parts = date_text.split("~")
                            start_part = parts[0].strip()
                            end_part = parts[1].strip()
                            
                            # Remove existing year if present to avoid double prefixing (simple check)
                            if start_part.startswith(year):
                                start_part = start_part.replace(year + ".", "")
                            if end_part.startswith(year):
                                end_part = end_part.replace(year + ".", "")

                            start_part = f"{year}.{start_part}"
                            end_part = f"{year}.{end_part}"
                            
                            date_text = f"{start_part} ~ {end_part}"
                        else:
                             if not date_text.startswith(year):
                                date_text = f"{year}.{date_text}"

                        start_date, end_date = self.parse_date_range(date_text)
                        
                        # Filter by date (today onwards - future events only)
                        today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                        if end_date and end_date < today:
                            continue

                        # Geocoding
                        lat, lng = self.geocode_place(place)
                        if lat == 0.0:
                             lat, lng = self.geocode_place(dong_name + " 주민자치센터")
                        
                        results.append({
                            "title": title,
                            "category": "교육/강좌",
                            "link": link,
                            "image": image,
                            "date": date_text,
                            "start_date": start_date,
                            "end_date": end_date,
                            "location": place,
                            "lat": lat,
                            "lng": lng,
                            "description": f"{dong_name} 주민자치센터 프로그램",
                            "source": "송도주민자치센터"
                        })
                    except Exception as e:
                        # print(f"Error parsing item in {dong_name}: {e}")
                        continue
                        
            except Exception as e:
                print(f"Error crawling {dong_name}: {e}")
                
        return results

    def parse_date_range(self, text):
        """
        Parses a date range string and returns start and end datetime objects.
        Supported formats:
        - YYYY.MM.DD ~ YYYY.MM.DD
        - YYYY-MM-DD ~ YYYY-MM-DD
        - YYYY.MM.DD
        """
        try:
            # Normalize separators
            text = text.replace("-", ".").replace(" ", "")
            
            # Extract dates
            dates = re.findall(r"(\d{4}\.\d{1,2}\.\d{1,2})", text)
            
            if len(dates) >= 2:
                start_str = dates[0]
                end_str = dates[1]
                start_date = datetime.strptime(start_str, "%Y.%m.%d")
                end_date = datetime.strptime(end_str, "%Y.%m.%d")
                return start_date, end_date
            elif len(dates) == 1:
                start_str = dates[0]
                start_date = datetime.strptime(start_str, "%Y.%m.%d")
                return start_date, start_date
            
        except Exception as e:
            # print(f"Date parsing error: {e}")
            pass
            
        return None, None

    def crawl_all(self):
        all_data = []
        # 1. Incheon Tour
        all_data.extend(self.crawl_incheon_tour())
        
        # 2. Yeonsu Cultural Foundation
        all_data.extend(self.crawl_yeonsu_foundation())
        
        # 3. Triple Street
        all_data.extend(self.crawl_triple_street())
        
        # 4. Incheon Cultural Foundation
        all_data.extend(self.crawl_incheon_cultural_foundation())

        # 5. Incheon Culture & Arts Center
        all_data.extend(self.crawl_incheon_arts_center())

        # 6. Songdo Convensia
        all_data.extend(self.crawl_songdo_convensia())

        # 7. Songdo Community Centers
        all_data.extend(self.crawl_songdo_community())
        
        return all_data

if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv() # 로컬 테스트용
    
    crawler = VenueCrawler()
    data = crawler.crawl_all()
    print(f"Total {len(data)} items collected")
    for item in data:
        print(f"- {item['title']} ({item['lat']}, {item['lng']})")
    crawler.close()
