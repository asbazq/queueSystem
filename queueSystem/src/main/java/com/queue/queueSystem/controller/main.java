// package com.queue.queueSystem.controller;

// import java.util.*;

// import java.util.*;


// class Solution {
//         public static void main(String[] args) {
//             // 입력 값 설정 (값 추가)
//             Map<String, Integer> inputMap = new HashMap<>();
//             inputMap.put("abcde", 3);
//             inputMap.put("abcjc", 3);
//             inputMap.put("hjklfsda", 1);
//             inputMap.put("hjkujki", 2);
//             inputMap.put("abcdef", 3);
//             inputMap.put("abcmno", 4);
//             inputMap.put("hjkxyz", 5);
//             inputMap.put("hjkrstu", 1);
    
//             // 결과를 담을 map
//             Map<String, Integer> resultMap = new HashMap<>();
            
//             // 입력 값의 접두어 찾기
//             for (String key : inputMap.keySet()) {
//                 addShortestPrefix(resultMap, key, inputMap.get(key));
//             }
    
//             // 결과 출력
//             for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
//                 System.out.println("Prefix: " + entry.getKey() + ", Result: " + entry.getValue());
//             }
//         }
    
//         // 최단 길이 접두어를 추가하는 메소드
//         private static void addShortestPrefix(Map<String, Integer> resultMap, String key, int value) {
//             for (int i = 1; i <= key.length(); i++) {
//                 String prefix = key.substring(0, i); // 현재 접두어
//                 // 이미 더 짧은 접두어에 값이 할당되어 있다면 멈춤
//                 if (resultMap.containsKey(prefix)) {
//                     if (resultMap.get(prefix) != value) {
//                         // 다른 값이면 충돌 방지
//                         break;
//                     }
//                     continue; // 같은 값이면 계속 탐색
//                 } else {
//                     // 값이 없으면 새로 할당하고 멈춤
//                     resultMap.put(prefix, value);
//                     break;
//                 }
//             }
//         }
//     }
    