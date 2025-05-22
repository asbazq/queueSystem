// src/App.js
import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import QueueModal from './QueueModal';

export default function App() {
  /* --------- State --------- */
  const [userId, setUserId]   = useState('');
  const [entered, setEntered] = useState(false);
  const [pos, setPos]         = useState(0);        // 내 순번
  const [active, setActive]   = useState(0);
  const [waiting, setWaiting] = useState(0);
  const [show, setShow]       = useState(false);    // 모달 표시 여부

  // 이전 waiting 값을 기억해 둡니다
  const waitingRef = useRef(0);
  const wsRef      = useRef(null);

  /* --------- 전체 상태 조회 함수 --------- */
  const fetchStatus = async () => {
    try {
      const { data } = await axios.get('/queue/status');
      setActive(data.active);
      setWaiting(data.waiting);
    } catch {
      // ignore
    }
  };

  /* --------- 마운트 시 5초마다 상태 폴링 --------- */
  useEffect(() => {
    fetchStatus();
    const id = setInterval(fetchStatus, 5000);
    return () => clearInterval(id);
  }, []);

  /* --------- 입장 함수 --------- */
  const enter = async () => {
    if (!userId) {
      alert('userId를 입력하세요.');
      return;
    }

    try {
      const { data } = await axios.post(
        '/queue/enter',
        null,
        { params: { userId } }
      );

      if (data.entered) {
        // 즉시 입장
        setEntered(true);
        setShow(false);
        setPos(0);
      } else {
        // 대기열에 들어감
        setEntered(false);
        setPos(data.position);          // 서버가 계산해 준 정확 순번
        setWaiting(data.position);      // 초깃값으로 waiting 상태 업데이트
        waitingRef.current = data.position; // waitingRef 초기화
        setShow(true);
      }
    } catch (err) {
      console.error(err);
      alert('입장 요청에 실패했습니다.');
    }
  };

  /* --------- WebSocket 연결 --------- */
  useEffect(() => {
    if (!userId) return;

    const ws = new WebSocket(
      `ws://localhost:8080/queue-status?userId=${encodeURIComponent(userId)}`
    );
    wsRef.current = ws;

    ws.onmessage = e => {
      if (!e.data.startsWith('{')) return; // JSON 아닌 메시지는 무시
      const msg = JSON.parse(e.data);

      // 개인 승격 알림
      if (msg.type === 'ENTER') {
        setEntered(true);
        setShow(false);
        setPos(0);
        return;
      }

      // 전체 현황 업데이트
      const w = msg.waiting ?? waitingRef.current;
      setActive(msg.active ?? active);
      setWaiting(w);

      // 앞 사람이 빠진 경우에만 내 순번 감소
      if (!entered && waitingRef.current > 0 && w < waitingRef.current) {
        const diff = waitingRef.current - w;
        setPos(prev => Math.max(1, prev - diff));
      }

      // waitingRef 갱신
      waitingRef.current = w;
    };

    // 언마운트 시 연결 종료
    return () => ws.close();
  }, [userId]); // entered나 pos 등 다른 상태가 바뀌어도 재연결하지 않습니다

  /* --------- UI 렌더링 --------- */
  return (
    <div style={{ padding: '2rem', fontFamily: 'sans-serif' }}>
      <h1>Queue Demo</h1>

      <input
        value={userId}
        onChange={e => setUserId(e.target.value)}
        placeholder="userId 입력"
        style={{ marginRight: '0.5rem' }}
      />
      <button onClick={enter}>입장</button>

      <hr />

      <p>{entered ? '✅ 서비스 이용 중' : '⏳ 대기 중'}</p>
      <p>접속: {active} / 대기: {waiting}</p>

      <QueueModal open={show && !entered} position={pos} />
    </div>
  );
}
