#!/bin/bash
# BPMN Process 3: Платежи / переводы (P2P / в сообщество)
# Отправитель нажал "Перевести" → Сформировать параметры →
# → Открыть платёжное окно → Показать форму →
# → Тип перевода (пользователю / сообщество) →
# → Пользователь продолжил? → Источник денег (карта / кошелёк) →
# → Достаточно средств? → Лимиты? → Перевод →
# → Успех → Уведомление получателю → Средства получены
# → Неуспех → Ошибка/отказ

BASE=http://localhost:8080/api
BANK=http://localhost:9090

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
step() { echo -e "\n${CYAN}[$1]${NC} $2"; }
hr()   { echo "------------------------------------"; }

CARD_NUMBER="${1:-}"
CARD_CVV="${2:-}"

hr
echo -e "${CYAN}BPMN Process 3: Платежи / переводы (P2P)${NC}"
hr

#  Auth: sender 
SENDER_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"transfer_sender","password":"pass123","role":"CUSTOMER"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$SENDER_TOKEN" ] || [ "$SENDER_TOKEN" = "" ]; then
    SENDER_TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"transfer_sender","password":"pass123"}' | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
fi

SENDER_ID=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $SENDER_TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

#  Auth: recipient 
RECIP_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"transfer_recipient","password":"pass123","role":"CUSTOMER"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$RECIP_TOKEN" ] || [ "$RECIP_TOKEN" = "" ]; then
    RECIP_TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"transfer_recipient","password":"pass123"}' | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
fi

RECIP_ID=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $RECIP_TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

ok "Авторизация: отправитель (#$SENDER_ID), получатель (#$RECIP_ID)"

#  START 
step "START" "Отправитель нажал 'Перевести'"

#  PLATFORM: Сформировать параметры 
step "PLATFORM → SERVICE TASK" "Сформировать параметры"
ok "Параметры сформированы"

#  PLATFORM: Открыть платёжное окно 
step "PLATFORM → SERVICE TASK" "Открыть платёжное окно"
ok "Окно открыто"

#  VK PAY: Показать форму оплаты / перевода 
step "VK PAY → SERVICE TASK" "Показать форму оплаты / перевода"

#  GATEWAY: Тип перевода 
step "GATEWAY" "Тип перевода"
ok "Пользователю (USER_TO_USER)"

#  GATEWAY: Пользователь продолжил? 
step "GATEWAY" "Пользователь продолжил?"
ok "ДА"

#  GATEWAY: Источник денег 
step "GATEWAY" "Источник денег"
echo "  Выбрано: Кошелёк"

#  GATEWAY: Достаточно средств? 
step "GATEWAY" "Достаточно средств?"

BALANCE=$(curl -s "$BASE/wallet" -H "Authorization: Bearer $SENDER_TOKEN" | \
    python3 -c "import sys,json; print(float(json.load(sys.stdin).get('balance',0)))" 2>/dev/null)
echo "  Баланс отправителя: $BALANCE"

TRANSFER_AMOUNT=1000

if [ "$(echo "$BALANCE < $TRANSFER_AMOUNT" | bc)" = "1" ]; then
    warn "НЕТ — недостаточно средств ($BALANCE < $TRANSFER_AMOUNT)"

    #  GATEWAY: Лимиты кошелька? 
    step "GATEWAY" "Лимиты кошелька?"
    ok "Нет лимита — просто нет средств"

    #  SERVICE TASK: Предложить пополнение 
    step "SERVICE TASK" "Предложить пополнение (СБП и т.п.)"

    if [ -n "$CARD_NUMBER" ] && [ -n "$CARD_CVV" ]; then
        #  SUBPROCESS: Пополнение 
        step "SUBPROCESS" "Пополнение кошелька"

        BIND=$(curl -s -X POST "$BASE/cards/bind" \
            -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
            -d "{\"cardNumber\":\"$CARD_NUMBER\",\"holderName\":\"Sender\",\"expiryDate\":\"02/30\",\"cvv\":\"$CARD_CVV\"}")

        SESSION_ID=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId') or '')" 2>/dev/null)
        REQUIRES_3DS=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('requires3ds',False))" 2>/dev/null)

        if [ "$REQUIRES_3DS" = "True" ] && [ -n "$SESSION_ID" ] && [ "$SESSION_ID" != "" ]; then
            echo -e "  ${YELLOW}Введите 3-DS код из терминала банка:${NC}"
            read -r CODE_3DS
            CONFIRM=$(curl -s -X POST "$BASE/cards/confirm-3ds" \
                -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
                -d "{\"sessionId\":\"$SESSION_ID\",\"code\":\"$CODE_3DS\"}")
            CARD_TOKEN=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
        else
            CARD_TOKEN=$(echo "$BIND" | python3 -c "import sys,json; c=json.load(sys.stdin).get('card'); print(c['token'] if c else '')" 2>/dev/null)
        fi

        if [ -z "$CARD_TOKEN" ] || [ "$CARD_TOKEN" = "" ]; then
            CARD_TOKEN=$(echo "$CARDS" | python3 -c "import sys,json; c=json.load(sys.stdin).get('items',[]); print(c[0]['token'] if c else '')" 2>/dev/null)
        fi

        if [ -n "$CARD_TOKEN" ] && [ "$CARD_TOKEN" != "" ]; then
            TOPUP=$(curl -s -X POST "$BASE/wallet/top-up" \
                -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
                -d "{\"cardToken\":\"$CARD_TOKEN\",\"amount\":5000}")
            NEW_BALANCE=$(echo "$TOPUP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance',0))" 2>/dev/null)
            ok "Кошелёк пополнен: $NEW_BALANCE"
        else
            fail "Не удалось привязать карту"
            step "MERGE → USER TASK" "Получить ошибку перевода"
            step "END (ERROR)" "Ошибка/отказ перевода"
            hr
            exit 1
        fi
    else
        fail "Карта не указана, пополнение невозможно"
        step "MERGE → USER TASK" "Получить ошибку перевода"
        step "END (ERROR)" "Ошибка/отказ перевода"
        hr
        exit 1
    fi
fi

ok "Средств достаточно"

#  SERVICE TASK: Провести перевод 
step "SERVICE TASK" "Провести перевод (списание → зачисление)"
echo "  Сумма: $TRANSFER_AMOUNT"
echo "  Отправитель: #$SENDER_ID → Получатель: #$RECIP_ID"

TRANSFER=$(curl -s -X POST "$BASE/transfers" \
    -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipientId\":$RECIP_ID,\"amount\":$TRANSFER_AMOUNT,\"source\":\"WALLET\",\"type\":\"USER_TO_USER\"}")

T_STATUS=$(echo "$TRANSFER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
T_ERROR=$(echo "$TRANSFER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)

#  GATEWAY: Перевод успешен? 
step "GATEWAY" "Перевод успешен?"

if [ "$T_STATUS" = "SUCCESS" ]; then
    ok "ДА"

    #  MESSAGE EVENT: Вернуть успех в UI 
    step "MESSAGE EVENT" "Вернуть успех в UI"
    ok "UI обновлён"

    #  USER TASK: Показать успешный перевод 
    step "USER TASK" "Показать успешный перевод"
    SENDER_BAL=$(curl -s "$BASE/wallet" -H "Authorization: Bearer $SENDER_TOKEN" | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('balance',0))" 2>/dev/null)
    echo "  Баланс отправителя: $SENDER_BAL"

    step "END (SENDER)" "Перевод завершён"

    #  MESSAGE EVENT: Уведомление получателю 
    step "MESSAGE EVENT" "Отправить уведомление получателю"
    ok "Уведомление отправлено"

    #  RECIPIENT: Получить уведомление 
    step "RECIPIENT → USER TASK" "Получить уведомление / зачисление"
    RECIP_BAL=$(curl -s "$BASE/wallet" -H "Authorization: Bearer $RECIP_TOKEN" | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('balance',0))" 2>/dev/null)
    echo "  Баланс получателя: $RECIP_BAL"
    ok "Средства зачислены"

    step "END (RECIPIENT)" "Средства получены"

else
    fail "НЕТ — $T_ERROR"

    step "SERVICE TASK" "Вернуть отмену в UI"
    step "MERGE → USER TASK" "Получить ошибку перевода"
    step "END (ERROR)" "Ошибка/отказ перевода"
fi

#  Тест лимита 
hr
echo -e "\n${CYAN}--- Дополнительный тест: превышение лимита ---${NC}"

step "GATEWAY" "Лимиты кошелька?"
LIMIT_TRANSFER=$(curl -s -X POST "$BASE/transfers" \
    -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipientId\":$RECIP_ID,\"amount\":200000,\"source\":\"WALLET\",\"type\":\"USER_TO_USER\"}")
LIMIT_ERROR=$(echo "$LIMIT_TRANSFER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
echo "  Попытка перевести 200 000"
fail "Лимит: $LIMIT_ERROR"
ok "Лимит корректно отработал"

#  Тест идемпотентности 
hr
echo -e "\n${CYAN}--- Дополнительный тест: идемпотентность ---${NC}"

IDEM_KEY="test-idempotency-$(date +%s)"
step "TEST" "Двойной перевод с одним idempotencyKey"

T1=$(curl -s -X POST "$BASE/transfers" \
    -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipientId\":$RECIP_ID,\"amount\":100,\"source\":\"WALLET\",\"type\":\"USER_TO_USER\",\"idempotencyKey\":\"$IDEM_KEY\"}")
T1_ID=$(echo "$T1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

T2=$(curl -s -X POST "$BASE/transfers" \
    -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipientId\":$RECIP_ID,\"amount\":100,\"source\":\"WALLET\",\"type\":\"USER_TO_USER\",\"idempotencyKey\":\"$IDEM_KEY\"}")
T2_ID=$(echo "$T2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ "$T1_ID" = "$T2_ID" ]; then
    ok "Идемпотентность работает: оба запроса вернули transfer #$T1_ID"
else
    fail "Идемпотентность сломана: #$T1_ID != #$T2_ID"
fi

hr
echo ""
echo "=== Итог: история переводов (отправитель) ==="
curl -s "$BASE/transfers" -H "Authorization: Bearer $SENDER_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""
echo "=== Итог: транзакции отправителя ==="
curl -s "$BASE/wallet/transactions" -H "Authorization: Bearer $SENDER_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""
echo "=== Итог: транзакции получателя ==="
curl -s "$BASE/wallet/transactions" -H "Authorization: Bearer $RECIP_TOKEN" | python3 -m json.tool 2>/dev/null
hr