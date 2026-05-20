package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.constant.KisConstants
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.java_websocket.WebSocket
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger(KisWebSocketHandler::class.java)

@Component
class KisWebSocketHandler(
    private val rtStockPriceStore: RealTimeStockPriceStore,
    private val objectMapper: ObjectMapper
) {
    private lateinit var approvalKey: String
    private var conn: WebSocket? = null
    private val subscribedStocks: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun setApprovalKey(approvalKey: String) {
        this.approvalKey = approvalKey
    }

    fun onOpen(conn: WebSocket) {
        this.conn = conn
        log.info("한국투자 증권 WebSocket 핸들러 연결 성공")
    }

    fun subscribe(stockCode: String) {
        if (!subscribedStocks.add(stockCode)) {
            log.info("이미 구독 중: {}", stockCode)
            return
        }

        val message = """
            {
              "header": {
                "approval_key": "$approvalKey",
                "custtype": "P",
                "tr_type": "1",
                "content-type": "utf-8"
              },
              "body": {
                "input": {
                  "tr_id": "${KisConstants.TR_REALTIME_PRICE}",
                  "tr_key": "$stockCode"
                }
              }
            }
        """.trimIndent()

        conn?.send(message)
        log.info("구독 시작: {}", stockCode)
    }

    fun unsubscribe(stockCode: String) {
        val message = """
            {
              "header": {
                "approval_key": "$approvalKey",
                "custtype": "P",
                "tr_type": "2",
                "content-type": "utf-8"
              },
              "body": {
                "input": {
                  "tr_id": "${KisConstants.TR_REALTIME_PRICE}",
                  "tr_key": "$stockCode"
                }
              }
            }
        """.trimIndent()

        conn?.send(message)
        log.info("구독 취소: {}", stockCode)
    }

    fun resubscribeAll() {
        if (subscribedStocks.isEmpty()) return  // 첫 연결 시 no-op
        log.info("재구독 시작 - {}개 종목", subscribedStocks.size)
        for (stockCode in subscribedStocks) {
            val message = """
            {
              "header": {
                "approval_key": "$approvalKey",
                "custtype": "P",
                "tr_type": "1",
                "content-type": "utf-8"
              },
              "body": {
                "input": {
                  "tr_id": "${KisConstants.TR_REALTIME_PRICE}",
                  "tr_key": "$stockCode"
                }
              }
            }
        """.trimIndent()
            conn?.send(message)
            log.info("재구독: {}", stockCode)
        }
    }

    fun onMessage(message: String) {
        if (message.startsWith("{")) {
            try {
                val json = objectMapper.readTree(message)
                val trId = json.path("header").path("tr_id").asText()
                log.info("tr_id: {}", trId)

                if (trId == "PINGPONG") {
                    log.info("PINGPONG 수신 → echo 응답")
                    conn?.send(message)
                    return
                }
            } catch (e: Exception) {
                log.warn("JSON 파싱 실패: {}", message)
            }

            log.info("📋 제어 메시지: {}", message)
        } else {
            parse(message)
        }
    }

    private fun parse(raw: String) {
        // 포맷: 0|H0STCNT0|004|005930^150000^70800^...
        val parts = raw.split("|")
        if (parts.size < 4) return

        val fields = parts[3].split("^")

        val stockPrice = RealtimeStockPrice(
            stockCode = fields[KisConstants.FIELD_STOCK_CODE],   // 종목 코드
            tradeTime = fields[KisConstants.FIELD_TRADE_TIME],   // 체결 시간
            price = fields[KisConstants.FIELD_CURRENT_PRICE],    // 주식 현재가
            changeSign = fields[KisConstants.FIELD_CHANGE_SIGN], // 전일 대비 부호
            change = fields[KisConstants.FIELD_CHANGE],          // 전일 대비
            changeRate = fields[KisConstants.FIELD_CHANGE_RATE]  // 전일 대비율
        )

        rtStockPriceStore.put(stockPrice.stockCode, stockPrice) // 캐싱
        // log.info("[{}] 체결시간: {}, 현재가: {}원 | 전일 대비 부호: {} | 전일 대비 가격: {} | 전일 대비율: {}",
        //     stockPrice.stockCode, stockPrice.tradeTime, stockPrice.price, stockPrice.changeSign,
        //     stockPrice.change, stockPrice.changeRate)
    }
}
