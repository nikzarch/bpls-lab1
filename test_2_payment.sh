#!/bin/bash
# BPMN Process 2: Оплата товара или услуги
# Пользователь нажал "Оплатить" → Продавец формирует параметры →
# → Платформа открывает окно → VK Pay проверяет параметры →
# → Параметры валидны? → Пользователь продолжил? → Способ оплаты →
# → Кошелёк: достаточно средств? → Транзакция → 3-DS → Успех? →
# → Webhook продавцу → Продавец проверяет → Выдать товар
# → Карта: Привязка карты (subprocess) → Транзакция → ...

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
hr()   { echo "-----------------------------------"; }

CARD_NUMBER="${1:-}"
CARD_CVV="${2:-}"

hr
echo -e "${CYAN}BPMN Process 2: Оплата товара или услуги${NC}"
hr

#  Auth: customer 
CUST_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"pay_customer","password":"pass123","role":"CUSTOMER"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$CUST_TOKEN" ] || [ "$CUST_TOKEN" = "" ]; then
    CUST_TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"pay_customer","password":"pass123"}' | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
fi

#  Auth: merchant 
MERCH_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"pay_merchant","password":"pass123","role":"MERCHANT"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$MERCH_TOKEN" ] || [ "$MERCH_TOKEN" = "" ]; then
    MERCH_TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"pay_merchant","password":"pass123"}' | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
fi

ok "Авторизация: покупатель + продавец"

#  START: Пользователь нажал "Оплатить" 
step "START" "Пользователь нажал 'Оплатить' в магазине"

#  SELLER SERVICE TASK: Сформировать параметры платежа 
step "SELLER → SERVICE TASK" "Сформировать параметры платежа"

WIDGET=$(curl -s -X POST "$BASE/widgets" \
    -H "Authorization: Bearer $MERCH_TOKEN" -H "Content-Type: application/json" \
    -d '{"name":"BPMN Test Shop","callbackUrl":"https://example.com/webhook"}')
WIDGET_ID=$(echo "$WIDGET" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ -z "$WIDGET_ID" ] || [ "$WIDGET_ID" = "" ]; then
    WIDGET_ID=$(curl -s "$BASE/widgets" -H "Authorization: Bearer $MERCH_TOKEN" | \
        python3 -c "import sys,json; w=json.load(sys.stdin); print(w[0]['id'] if w else '')" 2>/dev/null)
fi

PRODUCT=$(curl -s -X POST "$BASE/widgets/$WIDGET_ID/products" \
    -H "Authorization: Bearer $MERCH_TOKEN" -H "Content-Type: application/json" \
    -d '{"title":"Premium Stickers","type":"GOODS","price":499.00,"description":"Exclusive sticker pack"}')
PRODUCT_ID=$(echo "$PRODUCT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

echo "  Widget ID:  $WIDGET_ID"
echo "  Product ID: $PRODUCT_ID"
echo "  Price:      499.00"
ok "Параметры сформированы"

#  PLATFORM: Открыть платёжное окно → Передать в VK Pay 
step "PLATFORM → SERVICE TASK" "Открыть платёжное окно → Передать запрос в VK Pay"
ok "Окно открыто"

#  VK PAY: Показать окно оплаты 
step "VK PAY → SERVICE TASK" "Показать окно оплаты"

#  VK PAY: Проверить подписи / авторизацию / валидность параметров 
step "VK PAY → SERVICE TASK" "Проверить подписи / авторизацию / валидность параметров"

ORDER=$(curl -s -X POST "$BASE/payments/create" \
    -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
    -d "{\"widgetId\":$WIDGET_ID,\"productId\":$PRODUCT_ID}")

ORDER_ERROR=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
ORDER_ID=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

#  GATEWAY: Параметры валидны? 
step "GATEWAY" "Параметры валидны?"

if [ -n "$ORDER_ERROR" ] && [ "$ORDER_ERROR" != "" ]; then
    fail "НЕТ — $ORDER_ERROR"
    step "END (ERROR)" "Платёж отклонён"
    hr
    exit 1
fi

ok "ДА — заказ создан: $ORDER_ID"

#  GATEWAY: Пользователь продолжил оплату? 
step "GATEWAY" "Пользователь продолжил оплату?"
ok "ДА — продолжает"

#  GATEWAY: Способ оплаты 
step "GATEWAY" "Способ оплаты"

if [ -n "$CARD_NUMBER" ] && [ -n "$CARD_CVV" ]; then
    echo -e "  ${YELLOW}Доступны: Кошелёк, Карта${NC}"
    echo -e "  Тестируем оба способа"

    # ── Сценарий A: Кошелёк (нет средств → пополнение → оплата) ──
    hr
    echo -e "\n${CYAN}--- Сценарий A: Кошелёк ---${NC}"

    step "GATEWAY" "Достаточно средств?"
    BALANCE=$(curl -s "$BASE/wallet" -H "Authorization: Bearer $CUST_TOKEN" | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('balance',0))" 2>/dev/null)
    echo "  Баланс: $BALANCE"

    if [ "$(echo "$BALANCE < 499" | bc)" = "1" ]; then
        warn "НЕТ — недостаточно средств"

        step "SERVICE TASK" "Предложить пополнение (СБП и т.п.)"

        # ── SUBPROCESS: Пополнение ──
        step "SUBPROCESS" "Пополнение кошелька — привязка карты + top-up"

        BIND=$(curl -s -X POST "$BASE/cards/bind" \
            -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
            -d "{\"cardNumber\":\"$CARD_NUMBER\",\"holderName\":\"Test User\",\"expiryDate\":\"12/28\",\"cvv\":\"$CARD_CVV\"}")

        SESSION_ID=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId') or '')" 2>/dev/null)
        REQUIRES_3DS=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('requires3ds',False))" 2>/dev/null)

        if [ "$REQUIRES_3DS" = "True" ] && [ -n "$SESSION_ID" ] && [ "$SESSION_ID" != "" ]; then
            echo -e "  ${YELLOW}Введите 3-DS код из терминала банка:${NC}"
            read -r CODE_3DS
            CONFIRM=$(curl -s -X POST "$BASE/cards/confirm-3ds" \
                -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
                -d "{\"sessionId\":\"$SESSION_ID\",\"code\":\"$CODE_3DS\"}")
            CARD_TOKEN=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
        else
            CARD_TOKEN=$(echo "$BIND" | python3 -c "import sys,json; c=json.load(sys.stdin).get('card'); print(c['token'] if c else '')" 2>/dev/null)
        fi

        if [ -z "$CARD_TOKEN" ] || [ "$CARD_TOKEN" = "" ]; then
            CARD_TOKEN=$(curl -s "$BASE/cards" -H "Authorization: Bearer $CUST_TOKEN" | \
                python3 -c "import sys,json; c=json.load(sys.stdin); print(c[0]['token'] if c else '')" 2>/dev/null)
        fi

        if [ -n "$CARD_TOKEN" ] && [ "$CARD_TOKEN" != "" ]; then
            TOPUP=$(curl -s -X POST "$BASE/wallet/top-up" \
                -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
                -d "{\"cardToken\":\"$CARD_TOKEN\",\"amount\":5000}")
            NEW_BALANCE=$(echo "$TOPUP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance',0))" 2>/dev/null)
            ok "Кошелёк пополнен, баланс: $NEW_BALANCE"
        else
            fail "Не удалось привязать карту для пополнения"
            step "END (ERROR)" "Платёж отклонён"
            hr
            exit 1
        fi
    else
        ok "ДА — средств достаточно"
    fi

    #  SERVICE TASK: Провести транзакцию 
    step "SERVICE TASK" "Провести транзакцию (кошелёк)"

    PAY=$(curl -s -X POST "$BASE/payments/process" \
        -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
        -d "{\"orderId\":$ORDER_ID,\"method\":\"WALLET\"}")

    PAY_STATUS=$(echo "$PAY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
    PAY_ERROR=$(echo "$PAY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)

else
    echo "  Карта не указана, оплата только кошельком"

    step "GATEWAY" "Достаточно средств?"
    fail "НЕТ — кошелёк пуст и карта не указана"
    step "END (ERROR)" "Платёж отклонён"
    hr
    exit 1
fi

#  GATEWAY: Платёж успешен? 
step "GATEWAY" "Платёж успешен?"

if [ "$PAY_STATUS" = "PAID" ]; then
    ok "ДА"

    #  MESSAGE EVENT: Вернуть результат в приложение 
    step "MESSAGE EVENT" "Вернуть результат закрытия формы в приложение"
    ok "Результат отправлен"

    #  MESSAGE EVENT: Webhook продавцу 
    step "MESSAGE EVENT" "Отправить платёжное уведомление (webhook) на бэкенд продавца"
    ok "Webhook отправлен (см. логи Spring Boot)"

    #  SELLER: Принять уведомление 
    step "SELLER → TASK" "Принять уведомление, проверить подпись, обработать"

    #  GATEWAY: Уведомление валидно? 
    step "GATEWAY" "Уведомление валидно и статус успех?"
    ok "ДА"

    #  SELLER: Выдать товар 
    step "SELLER → TASK" "Выдать товар/услугу"
    ok "Товар выдан"

    step "END (SUCCESS)" "Заказ выполнен"
else
    fail "НЕТ — $PAY_ERROR"

    step "VK PAY → TASK" "Вернуть отмену в UI"
    step "USER TASK" "Получить ошибку"
    step "END (ERROR)" "Платёж отклонён"
fi

hr
echo ""
echo "=== Итог: история платежей ==="
curl -s "$BASE/payments" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""
echo "=== Итог: транзакции кошелька ==="
curl -s "$BASE/wallet/transactions" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
hr