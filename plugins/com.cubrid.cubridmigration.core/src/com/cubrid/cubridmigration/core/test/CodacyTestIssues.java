package com.cubrid.cubridmigration.core.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Codacy 설정 검증용 테스트 클래스
 * 의도적으로 다양한 이슈를 포함하여 Codacy 규칙이 제대로 작동하는지 확인
 */
public class CodacyTestIssues {

    // ==========================================
    // 1. Lizard Complexity 테스트 (CC > 15)
    // ==========================================
    
    /**
     * 매우 높은 사이클로매틱 복잡도 (CC 약 20)
     * Lizard Warning (CC > 15) 또는 Error (CC > 25) 발생 예상
     */
    public int veryComplexMethod(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        int result = 0;
        if (a > 0) {
            result++;
            if (b > 0) {
                result++;
                if (c > 0) {
                    result++;
                    if (d > 0) {
                        result++;
                        if (e > 0) {
                            result++;
                            if (f > 0) {
                                result++;
                                if (g > 0) {
                                    result++;
                                    if (h > 0) {
                                        result++;
                                        if (i > 0) {
                                            result++;
                                            if (j > 0) {
                                                result++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    // ==========================================
    // 2. Lizard File Length 테스트 (함수 길이 > 100)
    // ==========================================
    
    /**
     * 매우 긴 함수 (NLOC > 100)
     * Lizard Warning (NLOC > 100) 또는 Error (NLOC > 150) 발생 예상
     */
    public void veryLongMethod() {
        System.out.println("Line 1");
        System.out.println("Line 2");
        System.out.println("Line 3");
        System.out.println("Line 4");
        System.out.println("Line 5");
        System.out.println("Line 6");
        System.out.println("Line 7");
        System.out.println("Line 8");
        System.out.println("Line 9");
        System.out.println("Line 10");
        System.out.println("Line 11");
        System.out.println("Line 12");
        System.out.println("Line 13");
        System.out.println("Line 14");
        System.out.println("Line 15");
        System.out.println("Line 16");
        System.out.println("Line 17");
        System.out.println("Line 18");
        System.out.println("Line 19");
        System.out.println("Line 20");
        System.out.println("Line 21");
        System.out.println("Line 22");
        System.out.println("Line 23");
        System.out.println("Line 24");
        System.out.println("Line 25");
        System.out.println("Line 26");
        System.out.println("Line 27");
        System.out.println("Line 28");
        System.out.println("Line 29");
        System.out.println("Line 30");
        System.out.println("Line 31");
        System.out.println("Line 32");
        System.out.println("Line 33");
        System.out.println("Line 34");
        System.out.println("Line 35");
        System.out.println("Line 36");
        System.out.println("Line 37");
        System.out.println("Line 38");
        System.out.println("Line 39");
        System.out.println("Line 40");
        System.out.println("Line 41");
        System.out.println("Line 42");
        System.out.println("Line 43");
        System.out.println("Line 44");
        System.out.println("Line 45");
        System.out.println("Line 46");
        System.out.println("Line 47");
        System.out.println("Line 48");
        System.out.println("Line 49");
        System.out.println("Line 50");
        System.out.println("Line 51");
        System.out.println("Line 52");
        System.out.println("Line 53");
        System.out.println("Line 54");
        System.out.println("Line 55");
        System.out.println("Line 56");
        System.out.println("Line 57");
        System.out.println("Line 58");
        System.out.println("Line 59");
        System.out.println("Line 60");
        System.out.println("Line 61");
        System.out.println("Line 62");
        System.out.println("Line 63");
        System.out.println("Line 64");
        System.out.println("Line 65");
        System.out.println("Line 66");
        System.out.println("Line 67");
        System.out.println("Line 68");
        System.out.println("Line 69");
        System.out.println("Line 70");
        System.out.println("Line 71");
        System.out.println("Line 72");
        System.out.println("Line 73");
        System.out.println("Line 74");
        System.out.println("Line 75");
        System.out.println("Line 76");
        System.out.println("Line 77");
        System.out.println("Line 78");
        System.out.println("Line 79");
        System.out.println("Line 80");
        System.out.println("Line 81");
        System.out.println("Line 82");
        System.out.println("Line 83");
        System.out.println("Line 84");
        System.out.println("Line 85");
        System.out.println("Line 86");
        System.out.println("Line 87");
        System.out.println("Line 88");
        System.out.println("Line 89");
        System.out.println("Line 90");
        System.out.println("Line 91");
        System.out.println("Line 92");
        System.out.println("Line 93");
        System.out.println("Line 94");
        System.out.println("Line 95");
        System.out.println("Line 96");
        System.out.println("Line 97");
        System.out.println("Line 98");
        System.out.println("Line 99");
        System.out.println("Line 100");
        System.out.println("Line 101");
        System.out.println("Line 102");
        System.out.println("Line 103");
        System.out.println("Line 104");
        System.out.println("Line 105");
        System.out.println("Line 106");
        System.out.println("Line 107");
        System.out.println("Line 108");
        System.out.println("Line 109");
        System.out.println("Line 110");
    }

    // ==========================================
    // 3. Semgrep: SQL Injection 테스트
    // ==========================================
    
    /**
     * SQL Injection 취약점 - 문자열 연결
     * Semgrep: java.lang.security.audit.sqli.tainted-sql-from-http-request
     */
    public void sqlInjectionVulnerable(String userId, Statement stmt) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = " + userId;
        stmt.executeQuery(sql);
    }
    
    /**
     * SQL Injection 취약점 - String.format
     * Semgrep: java.lang.security.audit.formatted-sql-string
     */
    public void sqlInjectionViaFormat(String tableName, String columnName, Statement stmt) throws SQLException {
        String sql = String.format("SELECT %s FROM %s WHERE active = 1", columnName, tableName);
        stmt.executeQuery(sql);
    }
    
    /**
     * SQL Injection 취약점 - HttpServletRequest 파라미터
     * Semgrep: java.jboss.security.session_sqli.find-sql-string-concatenation
     */
    public void sqlInjectionFromRequest(HttpServletRequest req, Statement stmt) throws SQLException {
        String userInput = req.getParameter("query");
        stmt.executeQuery("SELECT * FROM data WHERE name = '" + userInput + "'");
    }

    // ==========================================
    // 4. Semgrep: Path Traversal 테스트
    // ==========================================
    
    /**
     * Path Traversal 취약점
     * Semgrep: java.lang.security.httpservlet-path-traversal
     */
    public void pathTraversalVulnerable(String filename) {
        File file = new File("/tmp/uploads/" + filename);
    }
    
    /**
     * Path Traversal via FileInputStream
     * Semgrep: path traversal via FileInputStream
     */
    public void pathTraversalFileInput(String userPath) throws Exception {
        FileInputStream fis = new FileInputStream("/data/" + userPath);
    }

    // ==========================================
    // 5. Semgrep: Weak Cryptography 테스트
    // ==========================================
    
    /**
     * MD5 해시 - 취약한 알고리즘
     * Semgrep: java.lang.security.audit.crypto.use-of-md5
     */
    public void weakHashMD5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(input.getBytes());
    }
    
    /**
     * SHA1 해시 - 취약한 알고리즘
     * Semgrep: java.lang.security.audit.crypto.use-of-sha1
     */
    public void weakHashSHA1(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(input.getBytes());
    }
    
    /**
     * DES 암호화 - 취약한 알고리즘
     * Semgrep: java.lang.security.audit.crypto.des-is-deprecated
     */
    public void weakCryptoDES() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DES");
    }
    
    /**
     * AES/ECB 모드 - 안전하지 않은 모드
     * Semgrep: java.lang.security.audit.crypto.use-of-aes-ecb
     */
    public void weakCryptoECB() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
    }
    
    /**
     * RC4 암호화 - 취약한 알고리즘
     * Semgrep: java.lang.security.audit.crypto.use-of-rc4
     */
    public void weakCryptoRC4() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RC4");
    }

    // ==========================================
    // 6. Semgrep: XXE (XML External Entity) 테스트
    // ==========================================
    
    /**
     * XXE 취약점 - DOCTYPE 선언 허용
     * Semgrep: java.lang.security.audit.xxe.documentbuilderfactory-disallow-doctype-decl-false
     */
    public void xxeVulnerable() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    }

    // ==========================================
    // 7. Semgrep: Unsafe Deserialization 테스트
    // ==========================================
    
    /**
     * 안전하지 않은 역직렬화
     * Semgrep: java.lang.security.jackson-unsafe-deserialization
     */
    public void unsafeDeserialization(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        mapper.enableDefaultTyping();
    }

    // ==========================================
    // 8. Semgrep: Command Injection 테스트
    // ==========================================
    
    /**
     * Command Injection 취약점
     * Semgrep: java.lang.security.audit.tainted-cmd-from-http-request
     */
    public void commandInjection(HttpServletRequest req) throws Exception {
        String cmd = req.getParameter("cmd");
        Runtime.getRuntime().exec(cmd);
    }

    // ==========================================
    // 9. PMD: Resource Leak 테스트 (CloseResource)
    // ==========================================
    
    /**
     * JDBC 리소스 누수 - Connection, Statement을 닫지 않음
     * PMD: CloseResource (CloseResource 규칙이 제외되지 않았는지 확인)
     */
    public void resourceLeakVulnerable() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:cubrid:localhost:33000:demodb", "dba", "");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT 1");
        // 리소스를 닫지 않음 - SpotBugs/PMD에서 감지되어야 함
    }
    
    /**
     * 파일 리소스 누수
     */
    public void fileResourceLeak() throws Exception {
        FileInputStream fis = new FileInputStream("/tmp/test.txt");
        // 스트림을 닫지 않음
    }

    // ==========================================
    // 10. PMD: 기타 Error Prone 패턴 테스트
    // ==========================================
    
    /**
     * Null 체크 누락
     */
    public void nullCheckMissing(String input) {
        System.out.println(input.length()); // NPE 가능성
    }
    
    /**
     * Equals null (잘못된 null 체크)
     */
    public boolean badNullCheck(String a, String b) {
        return a.equals(null) && b == null; // a.equals(null)는 잘못됨
    }

    // ==========================================
    // 11. 정상 코드 (Clean) - 대조군
    // ==========================================
    
    /**
     * 안전한 SQL 쿼리 - PreparedStatement 사용
     */
    public void safeSqlQuery(String userId, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        ps.setString(1, userId);
        ps.executeQuery();
        ps.close();
    }
    
    /**
     * 정상적인 복잡도 (CC < 15)
     */
    public int normalComplexity(int a, int b, int c) {
        if (a > 0) {
            if (b > 0) {
                return c;
            }
        }
        return 0;
    }
    
    /**
     * 안전한 해시 (SHA-256)
     */
    public void safeHash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(input.getBytes());
    }
}
