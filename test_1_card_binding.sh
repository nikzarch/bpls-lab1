#!/bin/bash
# BPMN Process 1: Привязка карты
# Пользователь → Выбрать "Привязать карту" → Форма ввода реквизитов →
# → Банк проверяет → 3-DS подтверждение (таймаут 5 мин, 3 попытки) →
# → Банк подтвердил? → Да: Сохранить привязку → Карта привязана
#                     → Нет: Показать ошибку → Ошибка привязки

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
hr()   { echo "-----------------------------"; }

CARD_NUMBER="${1:-}"
CARD_CVV="${2:-}"

if [ -z "$CARD_NUMBER" ] || [ -z "$CARD_CVV" ]; then
    echo "No card details provided, using defaults (will fail if bank emulator is running)"
fi

hr
echo -e "${CYAN}BPMN Process 1: Привязка карты${NC}"
hr

#  Проверка банка 
BANK_OK=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$BANK/validate" \
    -X POST -H "Content-Type: application/json" -d '{"card_number":"0000"}' 2>/dev/null)
if [ "$BANK_OK" = "000" ]; then
    fail "Bank emulator is not running at $BANK"
    exit 1
fi
ok "Bank emulator is running"

#  Регистрация / логин 
step "START" "Пользователь выбирает оплату картой / хочет сохранить карту"

TOKEN=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"card_user","password":"pass123","role":"CUSTOMER"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$TOKEN" ] || [ "$TOKEN" = "" ]; then
    TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"card_user","password":"pass123"}' | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
fi

if [ -z "$TOKEN" ]; then
    fail "Auth failed"
    exit 1
fi
ok "Пользователь авторизован"

#  TASK: Выбрать "Привязать карту" 
step "USER TASK" "Выбрать 'Привязать карту'"
echo "  Card: $CARD_NUMBER"
echo "  CVV:  $CARD_CVV"

#  SERVICE TASK: Показать форму ввода реквизитов 
step "SERVICE TASK" "Показать форму ввода реквизитов карты → отправить данные"

BIND=$(curl -s -X POST "$BASE/cards/bind" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"cardNumber\":\"$CARD_NUMBER\",\"holderName\":\"Test User\",\"expiryDate\":\"12/28\",\"cvv\":\"$CARD_CVV\"}")

ERROR=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)

#  GATEWAY: Карта проходит проверки у банка? 
step "GATEWAY" "Карта проходит проверки у банка?"

if [ -n "$ERROR" ] && [ "$ERROR" != "" ]; then
    fail "НЕТ — $ERROR"
    step "MERGE → SERVICE TASK" "Показать причину/ошибку"
    echo "  Ошибка: $ERROR"
    step "USER TASK" "Получить ошибку"
    step "END (ERROR)" "Ошибка привязки"
    hr
    exit 1
fi

ok "ДА — банк принял карту"

#  GATEWAY: Нужно 3-DS подтверждение? 
REQUIRES_3DS=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('requires3ds',False))" 2>/dev/null)
SESSION_ID=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId') or '')" 2>/dev/null)
DIRECT_TOKEN=$(echo "$BIND" | python3 -c "import sys,json; c=json.load(sys.stdin).get('card'); print(c['token'] if c else '')" 2>/dev/null)

step "GATEWAY" "Нужно 3-DS подтверждение?"

if [ "$REQUIRES_3DS" = "True" ] && [ -n "$SESSION_ID" ] && [ "$SESSION_ID" != "" ]; then
    ok "ДА — требуется 3-DS"
    echo "  Session: $SESSION_ID"

    #  SERVICE TASK: Ожидание подтверждения банка 
    step "SERVICE TASK" "Ожидание подтверждения банка (3-DS / SMS / push)"
    echo -e "  ${YELLOW}Таймаут: 5 минут, 3 попытки${NC}"
    echo ""
    echo -e "  ${YELLOW}Введите 3-DS код из терминала банка:${NC}"

    ATTEMPTS=0
    MAX_ATTEMPTS=3
    CONFIRMED=false

    while [ $ATTEMPTS -lt $MAX_ATTEMPTS ] && [ "$CONFIRMED" = "false" ]; do
        ATTEMPTS=$((ATTEMPTS + 1))
        read -r CODE_3DS
        echo ""

        CONFIRM=$(curl -s -X POST "$BASE/cards/confirm-3ds" \
            -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
            -d "{\"sessionId\":\"$SESSION_ID\",\"code\":\"$CODE_3DS\"}")

        CONFIRM_ERROR=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
        CARD_TOKEN=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

        if [ -n "$CARD_TOKEN" ] && [ "$CARD_TOKEN" != "" ]; then
            CONFIRMED=true
        else
            REMAINING=$((MAX_ATTEMPTS - ATTEMPTS))
            if [ $REMAINING -gt 0 ]; then
                warn "Неверный код. Осталось попыток: $REMAINING"
                echo -e "  ${YELLOW}Повторите ввод:${NC}"
            fi
        fi
    done

    #  GATEWAY: Банк подтвердил? 
    step "GATEWAY" "Банк подтвердил?"

    if [ "$CONFIRMED" = "true" ]; then
        ok "ДА — код верный"

        #  SERVICE TASK: Сохранить привязку 
        step "SERVICE TASK" "Сохранить привязку (токен/ссылка на карту)"
        MASKED=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('maskedCardNumber',''))" 2>/dev/null)
        echo "  Token:  $CARD_TOKEN"
        echo "  Masked: $MASKED"
        ok "Привязка сохранена"

        step "END (SUCCESS)" "Карта привязана"
    else
        fail "НЕТ — все попытки исчерпаны"
        step "MERGE → SERVICE TASK" "Показать причину/ошибку"
        echo "  Ошибка: 3-DS confirmation failed after $MAX_ATTEMPTS attempts"
        step "USER TASK" "Получить ошибку"
        step "END (ERROR)" "Ошибка привязки"
    fi

elif [ -n "$DIRECT_TOKEN" ] && [ "$DIRECT_TOKEN" != "" ]; then
    ok "НЕТ — 3-DS не требуется"

    step "SERVICE TASK" "Сохранить привязку (токен/ссылка на карту)"
    echo "  Token: $DIRECT_TOKEN"
    ok "Привязка сохранена"

    step "END (SUCCESS)" "Карта привязана"
else
    fail "Непредвиденная ошибка"
    echo "$BIND" | python3 -m json.tool 2>/dev/null
    step "END (ERROR)" "Ошибка привязки"
fi

hr
echo ""
echo "=== Итог: список карт ==="
curl -s "$BASE/cards" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool 2>/dev/null
hr