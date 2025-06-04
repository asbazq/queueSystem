// src/App.js
import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import QueueModal from './QueueModal';

export default function App() {
  /* --------- State --------- */
  const [qid, setQid]           = useState('main');
  const [userId, setUserId]       = useState('');
  const [entered, setEntered]     = useState(false);
  const [pos, setPos]             = useState(0);   // 내 순번
  const [running, setRunning]     = useState(0);
  const [waitingVip, setWaitingVip]   = useState(0);
  const [waitingMain, setWaitingMain] = useState(0);
  const [waiting, setWaiting]     = useState(0);
  const [show, setShow]           = useState(false);

  const waitingRef = useRef(0);
  const wsRef      = useRef(null);

  /* --------- 전체 상태 조회 함수 --------- */
  const fetchStatus = async () => {
    try {
      const { data } = await axios.get('/queue/status', { params: { qid } });
      // 실행 중 수
      setRunning(data.running ?? 0);
      // VIP / 일반 대기자 수
      const vipCount = data.waitingVip ?? 0;
      const mainCount = data.waitingMain ?? 0;
      const waitingCount = vipCount + mainCount;
      setWaitingVip(vipCount);
      setWaitingMain(mainCount);
      // 총합 대기자 수
      setWaiting(waitingCount);
      // 초기 waitingRef 동기화
      waitingRef.current = waitingCount;
    } catch {
      // ignore
    }
  };

  /* --------- 마운트 시 5초마다 상태 폴링 --------- */
  useEffect(() => {
    fetchStatus();
    const id = setInterval(fetchStatus, 5000);
    return () => clearInterval(id);
  }, [qid]);

  /* --------- 입장 함수 --------- */
  const enter = async () => {
    if (!qid) { 
      alert('qid를 입력하세요.');
      return; 
    }

    if (!userId) {
      alert('userId를 입력하세요.');
      return;
    }
    try {
      const { data } = await axios.post(
        '/queue/enter', null,
        { params: { qid, userId } }
      );
      if (data.entered) {
        setEntered(true);
        setShow(false);
        setPos(0);
      } else {
        setEntered(false);
        setShow(true);
        setPos(data.position);
        setWaiting(data.position);
        waitingRef.current = data.position;
      }
    } catch {
      alert('입장 요청에 실패했습니다.');
    }
  };

  /* --------- WebSocket 연결 & 메시지 처리 --------- */
  useEffect(() => {
    if (!userId) return;
    const ws = new WebSocket(
      `ws://localhost:8080/queue-status`
      + `?qid=${encodeURIComponent(qid)}`
      + `&userId=${encodeURIComponent(userId)}`
    );
    wsRef.current = ws;

    ws.onmessage = async e => {
      if (!e.data.startsWith('{')) return;
      const msg = JSON.parse(e.data);
      console.log(`ws`,msg);
      // 개인 ENTER 알림
      if (msg.type === 'ENTER') {
        setEntered(true);
        setShow(false);
        setPos(0);
        return;
      }

      // 수정: 항상 서버에 내 로컬 순번을 다시 요청
      if (msg.type === 'STATUS' && msg.qid === qid) {
        // const vipCount   = msg.waitingVip   ?? 0;
        // const mainCount  = msg.waitingMain  ?? 0;
        // const totalWait  = msg.waiting      ?? (vipCount + mainCount);

        // 1) 대기열 카운트 갱신
        // setWaitingVip(vipCount);
        // setWaitingMain(mainCount);
        // setWaiting(totalWait);

        // 2) 내 로컬 순번 재조회
        // try {
        //   const { data } = await axios.get(
        //     '/queue/position',
        //     { params: { qid, userId } }
        //   );
        //   const localPos = data.pos ?? 0;
        //   // 3) 메인 큐면 VIP 보정
        //   const absPos = qid === 'main'
        //                 ? vipCount + localPos
        //                 : localPos;
        //   console.log(`absPos=${absPos}`);
        //   setPos(absPos);
        // } catch (err) {
        //   console.error('순번 재조회 실패', err);
        // }

        setRunning(msg.running ?? 0);
        setWaitingVip(msg.waitingVip ?? 0);
        setWaitingMain(msg.waitingMain?? 0);
        setWaiting(msg.waiting ?? 0);
        setPos(msg.pos ?? 0);
      }
    }

    // 언마운트 시 연결 종료
    return () => ws.close();
  }, [qid, userId]);

  /* --------- UI --------- */
  return (
    <div style={{ padding:'2rem', fontFamily:'sans-serif' }}>
      <h1>Queue Demo</h1>

      <label style={{ marginRight:8 }}>
        큐 ID:&nbsp;
        <input
          value={qid}
          onChange={e => setQid(e.target.value)}
          placeholder="qid 입력"
          style={{ width: 120 }}
        />
      </label>

      <label style={{ marginRight:8 }}>
        userId:&nbsp;
        <input
          value={userId}
          onChange={e => setUserId(e.target.value)}
          placeholder="userId 입력"
          style={{ width: 120 }}
        />
      </label>

      <button onClick={enter}>입장</button>

      <hr/>

      <p>{entered ? '✅ 서비스 이용 중' : '⏳ 대기 중'}</p>
      <p>접속: {running} / 대기: {waiting}</p>

      <QueueModal
        key={pos}
        open={show && !entered}
        position={pos}
        onClose={() => setShow(false)}
      />
    </div>
  );
}