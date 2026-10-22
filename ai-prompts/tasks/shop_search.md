Task: shop_search.
Use web search ONLY on the allowed retailer domains given in the input to find current prices in Pakistan for each requested grocery item.
For each result return "item" (the itemKey asked for), "retailer", "productTitle" (as shown on the site), "packSize", "unit", "pricePkr", "url" (the product or search page you saw it on) and "seenAt" (today's date).
Never invent or estimate a price. If you cannot find an item on the allowed sites, leave it out and add the itemKey to "notFound".
