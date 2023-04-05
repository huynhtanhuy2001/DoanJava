package com.laptrinhoop.controller.web;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.laptrinhoop.entity.Order;
import com.laptrinhoop.service.IOrderSevice;
import com.laptrinhoop.service.impl.CartService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@Controller
public class OrderController {

	@Autowired
	private CartService cartService;

	@Autowired
	private IOrderSevice orderService;

//	@Autowired
//	private IRabbitmqService rabbit;

	@GetMapping("/order/checkout")
	public String checkOut(Model model,RedirectAttributes attributes) {
		if (cartService.getCountCart() == 0) {
			attributes.addFlashAttribute("message","Chưa có sản phẩm trong giỏ hàng");
			return "redirect:/cart/view";
		}
		model.addAttribute("cart", cartService);
		Order order = orderService.createOrder();
		model.addAttribute("order", order);
		return "order/checkout";
	}

	@PostMapping("/order/checkout")
	public String checkOut(Model model, @Validated @ModelAttribute("order") Order or) {
		 orderService.addOrderAndOrderDetail(or, cartService);
	//	rabbit.converToSendRabbit(or, cartService);
		cartService.clear();
		return "redirect:/home/index";
	}

	@GetMapping("/order/checkoutvnpay")
	public String checkOutvnpay(Model model,RedirectAttributes attributes) {
		if (cartService.getCountCart() == 0) {
			attributes.addFlashAttribute("message","Chưa có sản phẩm trong giỏ hàng");
			return "redirect:/cart/view";
		}
		model.addAttribute("cart", cartService);
		Order order = orderService.createOrder();
		model.addAttribute("order", order);
		return "order/checkoutvnpay";
	}

	@PostMapping("/order/checkoutvnpay")
	public ResponseEntity<Void> checkOutvnpay(Model model, @Validated @ModelAttribute("order") Order or) throws ServletException, IOException {
		orderService.addOrderAndOrderDetailViaVNPay(or, cartService);
		//	rabbit.converToSendRabbit(or, cartService);
		cartService.clear();

		int amount = (int) (or.getAmount() * 23000);
		System.out.println(amount);
		int amountToVND = amount * 100;
		System.out.println(amountToVND);
		System.out.println(or.getId());

		String vnp_Version = "2.1.0";
		String vnp_Command = "pay";
		String vnp_OrderInfo = or.getDescription();
		String orderType = "VNPAY";
		String vnp_TxnRef = "VH" + String.valueOf(or.getId());
		String vnp_IpAddr = "127.0.0.1";
		String vnp_TmnCode = "BY1BQ6TC";

		Map vnp_Params = new HashMap<>();
		vnp_Params.put("vnp_Version", vnp_Version);
		vnp_Params.put("vnp_Command", vnp_Command);
		vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
		vnp_Params.put("vnp_Amount", String.valueOf(amountToVND));
		vnp_Params.put("vnp_CurrCode", "VND");
		vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
		vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
		vnp_Params.put("vnp_OrderType", orderType);
		vnp_Params.put("vnp_Locale", "vn");
		vnp_Params.put("vnp_ReturnUrl", "http://127.0.0.1:8080/order/list");
		vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
		Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));

		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
		String vnp_CreateDate = formatter.format(cld.getTime());

		vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
		cld.add(Calendar.MINUTE, 15);
		String vnp_ExpireDate = formatter.format(cld.getTime());
		//Add Params of 2.1.0 Version
		vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);
		//Billing
		vnp_Params.put("vnp_Bill_Mobile", "0333598599");
		vnp_Params.put("vnp_Bill_Email", or.getCustomer().getEmail());
		String fullName = (or.getCustomer().getFullname()).trim();
		if (fullName != null && !fullName.isEmpty()) {
			int idx = fullName.indexOf(' ');
			String firstName = fullName.substring(0, idx);
			String lastName = fullName.substring(fullName.lastIndexOf(' ') + 1);
			vnp_Params.put("vnp_Bill_FirstName", firstName);
			vnp_Params.put("vnp_Bill_LastName", lastName);

		}
		vnp_Params.put("vnp_Bill_Address", or.getAddress());
		vnp_Params.put("vnp_Bill_City", "HCM");
		vnp_Params.put("vnp_Bill_Country", "VietNam");
		vnp_Params.put("vnp_Bill_State", "vnp_Bill_State");
		//Build data to hash and querystring
		List fieldNames = new ArrayList(vnp_Params.keySet());
		Collections.sort(fieldNames);
		StringBuilder hashData = new StringBuilder();
		StringBuilder query = new StringBuilder();
		Iterator itr = fieldNames.iterator();
		while (itr.hasNext()) {
			String fieldName = (String) itr.next();
			String fieldValue = (String) vnp_Params.get(fieldName);
			if ((fieldValue != null) && (fieldValue.length() > 0)) {
				//Build hash data
				hashData.append(fieldName);
				hashData.append('=');
				hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
				//Build query
				query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
				query.append('=');
				query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
				if (itr.hasNext()) {
					query.append('&');
					hashData.append('&');
				}
			}
		}
		String queryUrl = query.toString();
		String vnp_SecureHash = hmacSHA512("VITKNMDFMXEVXPNZMVTKOZUHUOFWVYKA", hashData.toString());
		queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
		String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html" + "?" + queryUrl;

		return redirect(paymentUrl);
	}


	@GetMapping
	ResponseEntity<Void> redirect(String paymentUrl) {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(paymentUrl))
				.build();
	}

	public static String hmacSHA512(final String key, final String data) {
		try {

			if (key == null || data == null) {
				throw new NullPointerException();
			}
			final Mac hmac512 = Mac.getInstance("HmacSHA512");
			byte[] hmacKeyBytes = key.getBytes();
			final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
			hmac512.init(secretKey);
			byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
			byte[] result = hmac512.doFinal(dataBytes);
			StringBuilder sb = new StringBuilder(2 * result.length);
			for (byte b : result) {
				sb.append(String.format("%02x", b & 0xff));
			}
			return sb.toString();

		} catch (Exception ex) {
			return "";
		}
	}

	@RequestMapping("/order/list")
	public String listOrder(Model model) {
		List<Order> list = orderService.getAllOrderByUser();
		model.addAttribute("orders", list);
		model.addAttribute("ordersWaiting",(List<Order>) list.stream().filter(item -> item.getStatus() == 1 || item.getStatus() == 5).collect(Collectors.toList()));
		model.addAttribute("ordersDelivery",
				(List<Order>) list.stream().filter(item -> item.getStatus() == 2).collect(Collectors.toList()));
		model.addAttribute("ordersDeliverted",
				(List<Order>) list.stream().filter(item -> item.getStatus() == 3).collect(Collectors.toList()));
		model.addAttribute("ordersCancel",
				(List<Order>) list.stream().filter(item -> item.getStatus() == 4).collect(Collectors.toList()));
		return "order/list";
	}

	@RequestMapping("/order/detail/{id}")
	public String detail(Model model, @PathVariable("id") Integer id) {
		Order order = orderService.findById(id);
		model.addAttribute("order", order);
		return "order/detail";
	}

	@RequestMapping("/order/items")
	public String getPurchasedItems(Model model) {
		model.addAttribute("list", orderService.getPurchasedItems().values());
		return "product/list";
	}
}
