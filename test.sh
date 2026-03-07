#!/bin/bash
BASE=http://localhost:8080/api
BANK=http://localhost:9090

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
hr()   { echo "---------------------------"; }

CARD_NUMBER="${1:-}"
CARD_CVV="${2:-}"

if [ -z "$CARD_NUMBER" ] || [ -z "$CARD_CVV" ]; then
    echo "No card details provided, using defaults (will fail if bank emulator is running)"
fi

echo "Clean previous data? (y/n)"
read -r CLEAN
if [ "$CLEAN" = "y" ]; then
    psql -U postgres -d labpay -c "
        TRUNCATE wallet_transactions, payment_orders, transfers,
                 card_binding_sessions, bank_cards, product_offers,
                 widgets, wallets, app_users RESTART IDENTITY CASCADE;
    "
    ok "Database cleaned"
fi

hr
echo "Card:  $CARD_NUMBER"
echo "CVV:   $CARD_CVV"
hr

echo ""
echo "Checking bank emulator..."
BANK_OK=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$BANK/validate" \
    -X POST -H "Content-Type: application/json" -d '{"card_number":"0000"}' 2>/dev/null)

if [ "$BANK_OK" = "000" ]; then
    warn "Bank emulator is DOWN at $BANK"
    warn "Card operations will fail, wallet operations will still work"
    BANK_UP=false
else
    ok "Bank emulator is running"
    BANK_UP=true
fi

hr
echo "=== 1. Register customer ==="
CUST=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"customer1","password":"pass123","role":"CUSTOMER"}')
echo "$CUST" | python3 -m json.tool 2>/dev/null || echo "$CUST"
CUST_TOKEN=$(echo "$CUST" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)

if [ -z "$CUST_TOKEN" ]; then
    warn "Customer may already exist, trying login..."
    CUST=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"customer1","password":"pass123"}')
    CUST_TOKEN=$(echo "$CUST" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)
fi

if [ -n "$CUST_TOKEN" ]; then
    ok "Customer token obtained"
else
    fail "Could not get customer token"
    exit 1
fi

hr
echo "=== 2. Register merchant ==="
MERCH=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"merchant1","password":"pass123","role":"MERCHANT"}')
MERCH_TOKEN=$(echo "$MERCH" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)

if [ -z "$MERCH_TOKEN" ]; then
    MERCH=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"merchant1","password":"pass123"}')
    MERCH_TOKEN=$(echo "$MERCH" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)
fi

if [ -n "$MERCH_TOKEN" ]; then
    ok "Merchant token obtained"
else
    fail "Could not get merchant token"
    exit 1
fi

hr
echo "=== 3. Register recipient ==="
RECIP=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d '{"username":"recipient1","password":"pass123","role":"CUSTOMER"}')
RECIP_TOKEN=$(echo "$RECIP" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)

if [ -z "$RECIP_TOKEN" ]; then
    RECIP=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"recipient1","password":"pass123"}')
    RECIP_TOKEN=$(echo "$RECIP" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)
fi

if [ -n "$RECIP_TOKEN" ]; then
    ok "Recipient token obtained"
else
    warn "Recipient registration/login failed (non-critical)"
fi

RECIP_ID=$(curl -s "$BASE/wallet" -H "Authorization: Bearer $RECIP_TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

hr
echo "=== 4. Bind card ==="
if [ "$BANK_UP" = true ]; then
    BIND=$(curl -s -X POST "$BASE/cards/bind" \
        -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
        -d "{\"cardNumber\":\"$CARD_NUMBER\",\"holderName\":\"TEST USER\",\"expiryDate\":\"12/28\",\"cvv\":\"$CARD_CVV\"}")
    echo "$BIND" | python3 -m json.tool 2>/dev/null || echo "$BIND"

    SESSION_ID=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId',''))" 2>/dev/null)
    REQUIRES_3DS=$(echo "$BIND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('requires3ds',False))" 2>/dev/null)

    if [ "$REQUIRES_3DS" = "True" ] && [ -n "$SESSION_ID" ]; then
        ok "3-DS required, session: $SESSION_ID"
        echo ""
        echo "Enter 3-DS code from bank emulator terminal:"
        read -r CODE_3DS

        echo ""
        echo "=== 5. Confirm 3-DS ==="
        CONFIRM=$(curl -s -X POST "$BASE/cards/confirm-3ds" \
            -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
            -d "{\"sessionId\":\"$SESSION_ID\",\"code\":\"$CODE_3DS\"}")
        echo "$CONFIRM" | python3 -m json.tool 2>/dev/null || echo "$CONFIRM"

        CARD_TOKEN=$(echo "$CONFIRM" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
        if [ -n "$CARD_TOKEN" ] && [ "$CARD_TOKEN" != "" ]; then
            ok "Card bound successfully, token: $CARD_TOKEN"
        else
            fail "3-DS confirmation failed"
        fi
    else
        fail "Bind failed or unexpected response"
    fi
else
    warn "Skipping card bind (bank is down)"
fi

hr
echo "=== 6. List cards ==="
CARDS=$(curl -s "$BASE/cards" -H "Authorization: Bearer $CUST_TOKEN")
echo "$CARDS" | python3 -m json.tool 2>/dev/null || echo "$CARDS"

if [ -z "$CARD_TOKEN" ]; then
    CARD_TOKEN=$(echo "$CARDS" | python3 -c "import sys,json; cards=json.load(sys.stdin); print(cards[0]['token'] if cards else '')" 2>/dev/null)
fi

hr
echo "=== 7. Check wallet ==="
curl -s "$BASE/wallet" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""

hr
echo "=== 8. Top up wallet ==="
if [ -n "$CARD_TOKEN" ] && [ "$CARD_TOKEN" != "" ]; then
    TOPUP=$(curl -s -X POST "$BASE/wallet/top-up" \
        -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
        -d "{\"cardToken\":\"$CARD_TOKEN\",\"amount\":10000}")
    echo "$TOPUP" | python3 -m json.tool 2>/dev/null || echo "$TOPUP"
    ok "Top-up requested"
else
    warn "No card token available, skipping top-up"
fi

hr
echo "=== 9. Wallet after top-up ==="
curl -s "$BASE/wallet" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""

hr
echo "=== 10. Create widget (merchant) ==="
WIDGET=$(curl -s -X POST "$BASE/widgets" \
    -H "Authorization: Bearer $MERCH_TOKEN" -H "Content-Type: application/json" \
    -d '{"name":"Test Shop","callbackUrl":"https://example.com/webhook"}')
echo "$WIDGET" | python3 -m json.tool 2>/dev/null || echo "$WIDGET"
WIDGET_ID=$(echo "$WIDGET" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ -z "$WIDGET_ID" ] || [ "$WIDGET_ID" = "" ]; then
    WIDGETS=$(curl -s "$BASE/widgets" -H "Authorization: Bearer $MERCH_TOKEN")
    WIDGET_ID=$(echo "$WIDGETS" | python3 -c "import sys,json; w=json.load(sys.stdin); print(w[0]['id'] if w else '')" 2>/dev/null)
fi

hr
echo "=== 11. Create product ==="
PRODUCT=$(curl -s -X POST "$BASE/widgets/$WIDGET_ID/products" \
    -H "Authorization: Bearer $MERCH_TOKEN" -H "Content-Type: application/json" \
    -d '{"title":"VK Stickers Pack","type":"GOODS","price":299.00,"description":"Cool stickers"}')
echo "$PRODUCT" | python3 -m json.tool 2>/dev/null || echo "$PRODUCT"
PRODUCT_ID=$(echo "$PRODUCT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

hr
echo "=== 12. Create payment order ==="
ORDER=$(curl -s -X POST "$BASE/payments/create" \
    -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
    -d "{\"widgetId\":$WIDGET_ID,\"productId\":$PRODUCT_ID}")
echo "$ORDER" | python3 -m json.tool 2>/dev/null || echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

hr
echo "=== 13. Process payment (wallet) ==="
PAY=$(curl -s -X POST "$BASE/payments/process" \
    -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
    -d "{\"orderId\":$ORDER_ID,\"method\":\"WALLET\"}")
echo "$PAY" | python3 -m json.tool 2>/dev/null || echo "$PAY"

PAY_STATUS=$(echo "$PAY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
if [ "$PAY_STATUS" = "PAID" ]; then
    ok "Payment successful"
else
    fail "Payment status: $PAY_STATUS"
fi

hr
echo "=== 14. P2P transfer ==="
RECIP_ID=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $RECIP_TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Recipient ID: $RECIP_ID"

TRANSFER=$(curl -s -X POST "$BASE/transfers" \
    -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipientId\":$RECIP_ID,\"amount\":500,\"source\":\"WALLET\",\"type\":\"USER_TO_USER\"}")
echo "$TRANSFER" | python3 -m json.tool 2>/dev/null || echo "$TRANSFER"

T_STATUS=$(echo "$TRANSFER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
if [ "$T_STATUS" = "SUCCESS" ]; then
    ok "Transfer successful"
else
    fail "Transfer status: $T_STATUS"
fi

hr
echo "=== 15. Transaction history ==="
curl -s "$BASE/wallet/transactions" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""

hr
echo "=== 16. Transfer history ==="
curl -s "$BASE/transfers" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""

hr
echo "=== 17. Payment history ==="
curl -s "$BASE/payments" -H "Authorization: Bearer $CUST_TOKEN" | python3 -m json.tool 2>/dev/null
echo ""

hr
echo -e "${GREEN}Done!${NC}"