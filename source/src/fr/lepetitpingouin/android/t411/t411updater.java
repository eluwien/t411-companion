package fr.lepetitpingouin.android.t411;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class t411updater extends Service {

	public Integer mails, oldmails;
	public double ratio;
	public String upload, download, username, conError = "", usernumber;

	AlarmManager alarmManager;
	PendingIntent pendingIntent;

	SharedPreferences prefs;
	BroadcastReceiver bR;

	int freq; // fr�quence de rafraichissement (en minutes)

	Intent i = new Intent("android.appwidget.action.APPWIDGET_UPDATE");

	// La page de login t411 :
	static final String CONNECTURL = "http://www.t411.me/users/login/?returnto=%2Fusers%2Fprofile%2F";

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
				
		Log.v("Service t411", "onStartCommand");

		Log.v("Service t411","Trying to register ACTION_TIME_TICK");
		bR = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				sendBroadcast(new Intent(
						"android.appwidget.action.APPWIDGET_UPDATE"));
				Log.v("ACTION_TIME_TICK", "Clock updated !");
			}
		};
		
		IntentFilter iF = new IntentFilter(Intent.ACTION_TIME_TICK);
		
		try{ // on essaye de d�senregistrer le receiver pour �viter les doublons
			unregisterReceiver(bR);
			}
		catch(Exception ex){
			Log.e("Cancel receiver :",ex.toString());
		}
		try {
			registerReceiver(bR, iF);
		} catch (Exception ex){Log.e("registerReceiver",ex.toString());}

		Log.v("Service t411","Trying to register ACTION_TIME_TICK : OK");
		
		// on charge les pr�f�rences de l'application
		prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		// notifier le d�but de la mise � jour, si l'option a �t� coch�e
		if (prefs.getBoolean("updAlert", false))
			createNotify(1992, R.drawable.ic_stat_updating,
					this.getString(R.string.notif_update),
					this.getString(R.string.notif_upd_title),
					this.getString(R.string.notif_upd_content), false,
					t411updater.class);

		try {
			// Mise � jour
			update(prefs.getString("login", ""),
					prefs.getString("password", ""));
		} catch (Exception ex) {
			Log.v("Credentials :",
					prefs.getString("login", "") + ":"
							+ prefs.getString("password", ""));
			Log.e("update", ex.toString());
		}

		Intent myIntent = new Intent(t411updater.this, t411updater.class);
		pendingIntent = PendingIntent.getService(t411updater.this, 0, myIntent,
				0);

		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

		freq = Integer.valueOf(prefs.getString("updateFreq", "15"));
		Log.v("Fr�quence avant v�rification", String.valueOf(freq));
		freq = (freq < 1) ? 1 : freq;
		Log.v("Fr�quence apr�s v�rification", String.valueOf(freq));

		// on d�finit une instance de Calendar selon la configuration
		// utilisateur (15 mn par d�faut)
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.add(Calendar.MINUTE, freq);

		try {
			// on annule l'alarme existante
			alarmManager.cancel(pendingIntent);
		} catch (Exception ex) {
			Log.e("AlarmManager", ex.toString());
		}
		
		// ...et on la reprogramme, si la config utilisateur le permet
		if (prefs.getBoolean("autoUpdate", false))
			alarmManager.set(AlarmManager.RTC_WAKEUP,
					calendar.getTimeInMillis(), pendingIntent);

		try {
			// annuler la notification de mise � jour
			cancelNotify(1992);
		} finally {
			//stopSelf();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		Log.v(this.getClass().getName(), "onDestroy");
		// � l'arret, annuler les r�p�titions
		alarmManager.cancel(pendingIntent);
		cancelNotify(1990);
		cancelNotify(1991);
		cancelNotify(1992);
		
		try{
			unregisterReceiver(bR);
			}
		catch(Exception ex){
			Log.e("Cancel receiver :",ex.toString());
		}
	}

	@SuppressWarnings("deprecation")
	public void update(String login, String password) throws IOException {
		Log.v("Service t411","Update()...");
		Connection.Response res = null;
		Document doc = null;
		// on ex�cute la requ�te HTTP, en passant le login et le password en
		// POST.
		res = Jsoup
				.connect(CONNECTURL)
				.data("login", login, "password", password)
				.method(Method.POST)
				//.userAgent(
				//		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.21 (KHTML, like Gecko) Chrome/19.0.1042.0 Safari/535.21")
				.timeout(15000) // timeout 15s pour �viter de figer le service
				.execute();

		Log.v("Service t411","JSoup ex�cut�");
		
		doc = res.parse();

		// on r�cup�re l'erreur de connexion, le cas �ch�ant
		try {
			conError = doc.select("#messages").first().text();
		} catch (Exception ex) {
			Log.e("conError", ex.toString());
		}
		if (conError != "") {
			Toast.makeText(getApplicationContext(), conError, Toast.LENGTH_LONG)
					.show();
		} else {
			// on r�cup�re le ratio, sous forme de Double � 2 d�cimales
			ratio = Math.round(Float.valueOf(doc.select(".rate").first().text()
					.replace(',', '.')) * 100.0) / 100.0;

			username = doc.select(".avatar-big").attr("alt");
			Log.v("username :", username);

			// on r�cup�re la chaine de l'upload sans la traiter, avec la fl�che
			// et l'unit�
			upload = doc.select(".up").first().text();
			Log.v("upload :", upload);

			// idem pour le download
			download = doc.select(".down").first().text();
			Log.v("download :", download);

			// et enfin le nombre de mails, sous forme d'entier
			oldmails = (mails != null) ? mails : prefs.getInt("lastMails", 0);
			Log.v("mails (avant check) :", oldmails.toString());
			mails = Integer.valueOf(doc.select(".mail  > strong").first()
					.text());
			Log.v("mails (apr�s check) :", mails.toString());
			
			// On r�cup�re aussi le N� utilisateur pour les statistiques
			String[] tmp = doc.select(".ajax").attr("href").split("=");
			usernumber = tmp[1];
			Log.v("user number = ",usernumber);

			// on stocke tout ce petit monde (si non nul) dans les pr�f�rences
			Editor editor = prefs.edit();
			if (mails != null)
				editor.putInt("lastMails", mails);
			if (upload != null)
				editor.putString("lastUpload", upload);
			if (download != null)
				editor.putString("lastDownload", download);
			if (ratio != Double.NaN)
				editor.putString("lastRatio", String.valueOf(ratio));
			editor.putString("usernumber", usernumber);

			Date date = new Date();
			editor.putString("lastDate", date.toLocaleString());
			editor.putString("lastUsername", username);
			
			/*// ---> transferred to MainActivity.java
			// Calcul du restant possible t�l�chargeable avant d'atteindre la limite de ratio fix�e
			double upData = getGigaOctetData(upload);
			double dlData = getGigaOctetData(download); 
			
			double lowRatio = Double.valueOf(prefs.getString("ratioMinimum", "1"));
			
			double beforeLimit = (upData-dlData*lowRatio)/lowRatio;
			
			String GoLeft = String.format("%.2f", beforeLimit)+" GB";
			
			editor.putString("GoLeft", (beforeLimit > 0)?GoLeft:"0 GB");
			Log.v("Restant a t�l�charger :", GoLeft);
			*/
			

			editor.commit();

			Log.v("t411 Error :", conError);
			Log.v("INFOS T411 :", "Mails (" + String.valueOf(mails) + ") "
					+ upload + " " + download + " " + String.valueOf(ratio));

			try {
				i.putExtra("ratio", String.valueOf(ratio));
				i.putExtra("upload", upload);
				i.putExtra("download", download);
				i.putExtra("mails", String.valueOf(mails));
				i.putExtra("username", username);
				Log.v("t41updater", "Envoi du Broadcast Intent");
				sendBroadcast(i);
				doNotify();
			} catch (Exception ex) {
				Log.v("Broadcast Sender", ex.toString());
			}
		}
	}
	
	/*public Double getGigaOctetData(String value) {		
		String[] array = value.split(" ");
		double data = Double.valueOf(array[1]);
		
		if(array[2].contains("MB")) // Mega-octet => ^2
			data = data/1024;
		if(array[2] == "TB") // Tera-octet => ^4
			data = data*1024;
		Log.v(array[1],String.valueOf(data));
		
		return data;
	}*/

	public void doNotify() {
		if (prefs.getBoolean("ratioAlert", false))
			if (ratio < Double.valueOf(prefs.getString("ratioMinimum", "0")))
				createNotify(1990, R.drawable.ic_stat_ratio,
						this.getString(R.string.notif_ratio),
						this.getString(R.string.notif_ratio_title) + " (< "
								+ prefs.getString("ratioMinimum", "???") + ")",
						this.getString(R.string.notif_ratio_content), true,
						Settings.class);
			else
				cancelNotify(1990);
		if (prefs.getBoolean("mailAlert", false))
			if (mails > oldmails)
				createNotify(1991, R.drawable.ic_stat_message,
						this.getString(R.string.notif_message),
						this.getString(R.string.notif_msg_title),
						this.getString(R.string.notif_msg_content), true,
						messagesActivity.class);
			else
				cancelNotify(1991);
	}

	@SuppressWarnings("deprecation")
	private void createNotify(int number, int icon, String label, String title,
			String description, boolean vibrate, Class<?> cls) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Notification notification = new Notification(icon, label,
				System.currentTimeMillis());

		if (cls != null)
			pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
					cls), 0);
		String titreNotification = title;
		String texteNotification = description;

		notification.setLatestEventInfo(this, titreNotification,
				texteNotification, pendingIntent);

		if (vibrate == true) {
			notification.defaults |= Notification.DEFAULT_SOUND;
			notification.defaults |= Notification.DEFAULT_VIBRATE;
			notification.defaults |= Notification.DEFAULT_LIGHTS;
		}

		notificationManager.notify(number, notification);
	}

	private void cancelNotify(int number) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(number);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.v("Binder", intent.toString());
		return null;
	}
}
