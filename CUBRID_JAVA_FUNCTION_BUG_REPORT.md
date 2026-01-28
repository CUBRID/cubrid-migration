# CUBRID Java 저장 함수 중첩 호출 버그 리포트

## ⚠️ 버그 발생 조건 (핵심 요약)

**언제 버그가 발생하나요?**

1. **Java 저장 함수 안에서 다른 Java 저장 함수를 호출할 때** (중첩 호출)
2. **그 함수 호출이 마지막 인자가 아닌 위치에 있을 때** (첫 번째 또는 중간 인자)

**구체적인 예시:**

✅ **정상 동작** (버그 없음):
```sql
-- 함수 호출이 마지막 인자 위치
select fn_concat2('123', fn_concat2('123','456'));  -- OK!
select fn_concat3('123', '456', fn_concat2('12','34'));  -- OK!
```

❌ **버그 발생** (오류 발생):
```sql
-- 함수 호출이 첫 번째 인자 위치
select fn_concat2(fn_concat2('123','456'), '123');  -- ERROR!

-- 함수 호출이 중간 인자 위치  
select fn_concat3('123', fn_concat2('45','67'), '123');  -- ERROR!
```

**요약**: Java 저장 함수를 다른 Java 저장 함수의 **인자로 사용**할 때, 그 인자가 **마지막이 아니면** 버그 발생!

---

## 문제 요약

CUBRID 11.4에서 Java 저장 함수를 중첩 호출할 때, 함수 호출이 인자의 위치에 따라 오류가 발생하거나 정상 동작하는 현상이 발생합니다.

## 재현 환경

### 1. Java 클래스 생성

```java
public class cubTest {
    public static String concatThree(String s1, String s2, String s3) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        if (s3 == null) s3 = "";
        return s1 + s2 + s3;
    }

    public static String concatTwo(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        return s1 + s2;
    }
}
```

### 2. 함수 생성

```sql
CREATE FUNCTION fn_concat3(a VARCHAR, b VARCHAR, c VARCHAR) RETURN VARCHAR
AS LANGUAGE JAVA
NAME 'cubTest.concatThree(java.lang.String, java.lang.String, java.lang.String) return java.lang.String';

CREATE FUNCTION fn_concat2(a VARCHAR, b VARCHAR) RETURN VARCHAR
AS LANGUAGE JAVA
NAME 'cubTest.concatTwo(java.lang.String, java.lang.String) return java.lang.String';
```

## 테스트 결과

### 기본 함수 동작 확인 (정상)
```sql
select fn_concat2('123','456');        -- ✅ '123456'
select fn_concat3('12','34','56');     -- ✅ '123456'
```

### 중첩 함수 호출 테스트

#### ❌ 오류 발생 케이스
```sql
-- 함수가 첫 번째 인자
select fn_concat2(fn_concat2('123','456'), '123');
-- ERROR: Stored procedure execute error: 1

-- 함수가 중간 인자
select fn_concat3('123', fn_concat2('45','67'), '123');
-- ERROR: Stored procedure execute error: 2
```

#### ✅ 정상 동작 케이스
```sql
-- 함수가 마지막 인자
select fn_concat2('123', fn_concat2('123','456'));        -- ✅ '123123456'
select fn_concat3('123', '456', fn_concat2('12','34'));   -- ✅ '1234561234'
```

## 문제 분석

### 패턴 분석

1. **정상 동작**: 함수 호출이 **마지막 인자 위치**에 있을 때만 정상 동작
2. **오류 발생**: 함수 호출이 **첫 번째 인자** 또는 **중간 인자** 위치에 있을 때 오류 발생

### 추정 원인

CUBRID의 Java 저장 함수 호출 메커니즘에서 다음과 같은 문제가 있을 것으로 추정됩니다:

1. **인자 평가 순서 문제**: 함수 인자를 왼쪽에서 오른쪽으로 평가하는 과정에서, 중첩된 함수 호출의 반환값을 처리하는 로직에 버그가 있을 가능성
2. **스택 관리 문제**: Java 저장 함수 호출 시 내부 스택이나 컨텍스트 관리에서, 중첩 호출이 첫 번째나 중간 인자에 있을 때 스택 상태가 잘못 관리되는 문제
3. **인자 전달 메커니즘**: Java 메서드로 인자를 전달하는 과정에서, 중첩 함수 호출의 반환값이 첫 번째나 중간 위치에 있을 때 올바르게 처리되지 않는 문제

### 오류 코드 분석

- `ERROR: Stored procedure execute error: 1` - 첫 번째 인자 위치에서 함수 호출 시 발생
- `ERROR: Stored procedure execute error: 2` - 두 번째(중간) 인자 위치에서 함수 호출 시 발생

오류 코드의 숫자가 인자 위치 인덱스와 일치하는 것으로 보아, 인자 처리 과정에서 해당 위치의 값을 가져오거나 전달하는 단계에서 문제가 발생하는 것으로 추정됩니다.

## 영향도

- **심각도**: 높음 - Java 저장 함수를 사용하는 애플리케이션에서 중첩 호출이 필요한 경우 기능이 제한됨
- **영향 범위**: CUBRID 11.4에서 Java 저장 함수를 사용하는 모든 사용자
- **우회 방법**: 함수 호출을 마지막 인자 위치에 배치하거나, 임시 변수를 사용하여 중첩 호출을 피하는 방법

## 권장 사항

1. **CUBRID 개발팀에 버그 리포트 제출**
2. **임시 우회 방법 사용**:
   - 중첩 함수 호출을 피하고 임시 변수 사용
   - 가능한 경우 함수 호출을 마지막 인자 위치에 배치
3. **대안 검토**:
   - PL/CSQL 함수 사용 검토
   - 애플리케이션 레벨에서 문자열 연결 처리

## 테스트 케이스

### 추가 테스트가 필요한 케이스

1. 3개 이상의 인자를 가진 함수에서 중간 위치 함수 호출
2. 여러 개의 중첩 함수 호출이 있는 경우
3. 다른 데이터 타입(VARCHAR 외)을 사용하는 경우
4. NULL 값을 반환하는 중첩 함수 호출

## 버전 정보

- **CUBRID 버전**: 11.4
- **작성일**: 2024
- **재현 가능성**: 항상 재현 가능
