package stronger.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.springframework.beans.factory.annotation.Autowired;

import exceptions.CurrencyNotFoundException;
import exceptions.InternalErrorException;

import rates.adapter.ExchangeRatesAdapter;

import model.ExchangeRatesResponse;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.functions.BiFunction;

public class StrongerServiceImpl implements StrongerService {

	private static final String HISTORY_RATE_BASE_END_POINT = "http://data.fixer.io/api/%s?base=%s&access_key=%s";
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private ExchangeRatesAdapter ratesAdapter;
	
	@Autowired
	public StrongerServiceImpl(ExchangeRatesAdapter exchangeRatesAdapter) {
		
		this.ratesAdapter = exchangeRatesAdapter;
	}

	public Single<Boolean> isStronger(final String baseCurrency, final String counterCurrency, final String accessKey) {

		return Observable.zip(
				ratesAdapter.getExchangeRates(baseCurrency, accessKey).toObservable(),
				yesterdayRate(baseCurrency, accessKey),
				new BiFunction<ExchangeRatesResponse, ExchangeRatesResponse, Boolean>() {
					public Boolean apply(ExchangeRatesResponse t1, ExchangeRatesResponse t2) throws Exception {

						BigDecimal todayRate = t1.getRates().get(counterCurrency);
						BigDecimal yesterdayRate = t2.getRates().get(counterCurrency);
						
						if (todayRate == null || yesterdayRate == null) {
							throw new CurrencyNotFoundException();
						}
						
						return todayRate.compareTo(yesterdayRate) > 0;
					}
		}).toSingle();
	}

	private Observable<ExchangeRatesResponse> yesterdayRate(final String baseCurrency, final String accessKey) {
		
		return Observable.create(new ObservableOnSubscribe<ExchangeRatesResponse>() {

			public void subscribe(ObservableEmitter<ExchangeRatesResponse> emitter) throws Exception {
				
				try {
					String yesterdaysDate = getYesterdaysDateFormatted();
					String endPoint = String.format(HISTORY_RATE_BASE_END_POINT, yesterdaysDate, baseCurrency, accessKey);
		    		URL obj = new URL(endPoint);
		    		
		    		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		    		con.setRequestMethod("GET");

		    		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		    		ExchangeRatesResponse response = ratesAdapter.readRatesFromResponse(in);

		    		emitter.onNext(response);
		    		emitter.onComplete();
		    		
				} catch (Exception e) {
					emitter.onError(new InternalErrorException());
				}
			}
		});
	}
	
	private String getYesterdaysDateFormatted() {
		
		DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, -30);
		return dateFormat.format(calendar.getTime());
	}
}
