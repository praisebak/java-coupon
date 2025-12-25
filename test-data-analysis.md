# 쿠폰 테스트 데이터 삽입 쿼리 분석

## 발견된 문제점

### 1. 컬럼명 불일치 가능성
JPA가 자동으로 생성하는 컬럼명과 SQL의 컬럼명이 다를 수 있습니다.

**예상 컬럼명** (JPA 기본 변환):
- `title` → `title` ✅
- `discountAmount` → `discount_amount` ✅
- `minimumOrderPrice` → `minimum_order_price` ✅
- `totalQuantity` → `total_quantity` ✅
- `issuedQuantity` → `issued_quantity` ✅
- `validStartedAt` → `valid_started_at` ✅
- `validEndedAt` → `valid_ended_at` ✅
- `version` → `version` ✅
- `createdAt` → `created_at` ✅

### 2. 서브쿼리 구조의 복잡성
서브쿼리가 정확히 1000개 행을 생성하는지 확인 필요합니다.
- `t1`과 `t2`의 CROSS JOIN = 100개
- `t3`과 CROSS JOIN = 400개
- `LIMIT 1000`으로 제한

하지만 `@row` 변수 초기화 방식이 복잡해서 예상치 못한 동작이 있을 수 있습니다.

### 3. 변수 초기화 타이밍
`@row := (i-1)*1000` 부분이 서브쿼리 내부에서 실행되는데, 외부 루프 변수 `i`를 참조하는 방식이 MySQL 버전에 따라 다르게 동작할 수 있습니다.

## 개선된 쿼리 제안

더 간단하고 확실한 방법:

```sql
DELIMITER $$

CREATE PROCEDURE insert_test_data()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE j INT DEFAULT 1;
  
  WHILE i <= 10 DO
    SET j = 1;
    WHILE j <= 1000 DO
      INSERT INTO coupons (
        title, 
        discount_amount, 
        minimum_order_price, 
        total_quantity, 
        issued_quantity, 
        valid_started_at, 
        valid_ended_at, 
        version, 
        created_at
      ) VALUES (
        CONCAT('쿠폰_', LPAD((i-1)*1000 + j, 5, '0')),
        FLOOR(1000 + (RAND() * 10000)),
        FLOOR(10000 + (RAND() * 50000)),
        CASE WHEN j % 10 = 0 THEN NULL ELSE FLOOR(100 + (RAND() * 900)) END,
        0,
        NOW() - INTERVAL (RAND() * 30) DAY,
        NOW() + INTERVAL (30 + RAND() * 90) DAY,
        0,
        NOW() - INTERVAL (RAND() * 60) DAY
      );
      SET j = j + 1;
    END WHILE;
    SET i = i + 1;
  END WHILE;
END$$

DELIMITER ;

CALL insert_test_data();
DROP PROCEDURE insert_test_data;
```

또는 더 빠른 방법 (단일 INSERT with VALUES):

```sql
-- 10,000개 쿠폰 삽입 (더 빠름)
INSERT INTO coupons (
  title, 
  discount_amount, 
  minimum_order_price, 
  total_quantity, 
  issued_quantity, 
  valid_started_at, 
  valid_ended_at, 
  version, 
  created_at
)
SELECT 
  CONCAT('쿠폰_', LPAD(seq.n, 5, '0')) as title,
  FLOOR(1000 + (RAND() * 10000)) as discount_amount,
  FLOOR(10000 + (RAND() * 50000)) as minimum_order_price,
  CASE WHEN seq.n % 10 = 0 THEN NULL ELSE FLOOR(100 + (RAND() * 900)) END as total_quantity,
  0 as issued_quantity,
  NOW() - INTERVAL (RAND() * 30) DAY as valid_started_at,
  NOW() + INTERVAL (30 + RAND() * 90) DAY as valid_ended_at,
  0 as version,
  NOW() - INTERVAL (RAND() * 60) DAY as created_at
FROM (
  SELECT @row := @row + 1 as n
  FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t3,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t4,
       (SELECT @row := 0) r
  LIMIT 10000
) seq;
```

## 권장 사항

1. **먼저 테이블 구조 확인**:
   ```sql
   DESCRIBE coupons;
   -- 또는
   SHOW CREATE TABLE coupons;
   ```

2. **작은 데이터로 테스트**:
   - 프로시저 없이 먼저 10개 정도만 삽입해보기
   - 컬럼명과 데이터 타입 확인

3. **성능 고려**:
   - 10,000개를 한 번에 삽입하면 더 빠름
   - 트랜잭션 처리 방식 확인


