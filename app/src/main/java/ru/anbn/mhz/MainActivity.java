package ru.anbn.mhz;

import static android.content.ContentValues.TAG;
import static java.lang.Thread.sleep;
import static ru.anbn.mhz.StaticVariables.FILE_PATH_LOCAL_DATA;
import static ru.anbn.mhz.StaticVariables.FILE_PATH_LOCAL_DATA_TEMP;
import static ru.anbn.mhz.StaticVariables.FILE_PATH_YANDEX_DISK_DATA;
import static ru.anbn.mhz.StaticVariables.radioFrequencyChannel;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {
    // количество строк в файле mhz_data.txt
    public static int countRows;
    private String[][] sData = null;
    // массив для хранения десятичных координат станций широты и долготы соответственно
    public static double[][] dGeographicCoordinates = null;

    // счетчик для числа переходов
    private static int countSleep;
    private static int timerSeconds = 10;

    // id загрузки файла в менеджере
    private static long downloadId;

    // массив для дальнейшего заполнения найденными позициями при поиске станций
    private ArrayList<Integer> integerArrayList = new ArrayList<Integer>();
    private ArrayList<String> stringArrayList = new ArrayList<String>();

    // в эти переменные запишем строки приведенные к верхнему регистру
    private String sArrayUpper, sSearchUpper;

    // вспомогательная текстовая переменная
    private String sTemp;

    // номер выбранной позиции в ListView
    private int number;

    // переменная хранит состояние поиска
    private boolean bSearch;

    // переменная хранит состояние видимости textView
    private boolean bVisibleTextView = false;

    // !!! Подумать над этим блока
    // Посмотреть как заметки оставлять
    protected static String fileName = "";
    protected static String choiceFrequency = "";

    // информация о синхронизации данных
    private static boolean bSynchronizationIsCompleted = false;
    private static boolean bFirstRun = false;

    // тип выбранной радиостанции
    private static String typeOfRadioStation = "notSelected";

    // индекс во вспомогательном массиве для отображения параметров настройки радиостанции
    private static int index = -1;

    // переменная для обработки статуса поиска координат
    public static boolean bGPSCoordinatesFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // запретим ночную тему
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // запрос разрешение на использовние геопозиции
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                StaticVariables.MY_PERMISSIONS_REQUEST_GPS);

        // зададим идентификаторы полям spinner
        final Spinner spinner = findViewById(R.id.spinner);

        // адаптер для spinner1 со списком оборудования
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.
                R.layout.simple_spinner_item, Variables.SEQUIPMENT);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);


        // устанавливаем обработчик нажатия spinner1
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // выбрана позиция 0: Выберите оборудование
                if (position == 0) {
                    //displayToast("Выберите оборудование");
                    fileName = "";
                    typeOfRadioStation = "notSelected";
                }

                // выбрана позиция 1: РЛСМ-10
                if (position == 1) {
                    fileName = "rlsm10.pdf";
                    typeOfRadioStation = "РЛСМ-10";
                }

                // выбрана позиция 1: РВ-1.1М
                if (position == 2) {
                    fileName = "rv1_1m.pdf";
                    typeOfRadioStation = "РВ-1.1М";
                }

                // выбрана позиция 1: РВ-1М
                if (position == 3) {
                    fileName = "rv1m.pdf";
                    typeOfRadioStation = "РВ-1М";
                }

                // выбрана позиция 1: РВС-1
                if (position == 4) {
                    fileName = "rvs1.pdf";
                    typeOfRadioStation = "РВС-1";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        /* зададим listener для поиска станции по двум и более введенным символам
           в поле searchView */
        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // обработчик нажатия кнопки поиска поля searchView1
            @Override
            public boolean onQueryTextSubmit(String query) {
                // User pressed the search button
                return false;
            }

            // обработчик ввода символа поля searchView
            @Override
            public boolean onQueryTextChange(String sSearch) {
                integerArrayList.clear();
                stringArrayList.clear();
                sSearchUpper = sSearch.toUpperCase();
                bSearch = false;

                choiceFrequency = "";

                // проверим что длина введенной строки более 2х символов, тогда поиск совпадений
                if (sSearch.length() > 1) {

                    for (int i = 2; i < countRows; i++) {
                        sArrayUpper = sData[i][2].toUpperCase();
                        // проверим вхождение искомой строки в название станции в массиве
                        if (sArrayUpper.indexOf(sSearchUpper) != -1) {
                            /* сохраним индекс позиции с соответствием текста
                               в дальнейшем по этим индексам будем выводить информацию */
                            integerArrayList.add(i);
                            sTemp = sData[i][2] + " " + sData[i][1]; // + "  " + sData[i][2];
                            stringArrayList.add(sTemp);
                            bSearch = true;
                        }
                    }
                }

                // проверим успешный ли был поиск
                if (bSearch == false) {
                    // поиск не дал результата, очистим ListView от информации
                    // после предыдущего поиска
                    listViewClear();

                } else {
                    // поиск успешен, делаем textView invisible и выводим результаты в listView
                    textViewInvisible();
                    searchResultsDisplay();
                }

                // listener ListView слушает клики. При выборе позиции закрываем listView
                // и отображаем данные в формате: станция, регион
                return false;
            }
        });

        // метод прослушивания нажатий на ListView, выбор нужной позиции и отображение результата
        ListView listView = findViewById(R.id.listView);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View itemClicked, int position, long id) {
                // используем position для получения номера пункта по которому кликнул пользователь
                number = integerArrayList.get(position);

                // прячем клавиатуру. butCalculate - это кнопка
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchView.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);

                // отобразим выбранную позицию
                displayTheSelectedPositionListView();
            }
        });

    }

    // вывод на экрон toast
    public void displayToast(String sText) {
        //создаём и отображаем текстовое уведомление
        Toast toast = Toast.makeText(this, sText, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
    }


    // делаем textView invisible and clear
    public void textViewInvisible() {
        TextView textView1 = findViewById(R.id.textView1);
        textView1.setVisibility(View.INVISIBLE);
        bVisibleTextView = false;
    }

    // отображение выбранной в ListView позиции
    public void displayTheSelectedPositionListView() {
        // делаем ListView невидимым
        listViewInvisible();

        index = -1;
        // в зависимости от номера частоты выведем соответствующие данные из radioFrequencyChannel
        if (sData[number][3].equals("Нет данных")) {
            // нет данных по станции
            // сформируем данные для отображения
            sTemp = "\n" +
                    "   Дорога:    " + sData[number][0] + "\n" +
                    "   Регион:    " + sData[number][1] + "\n" +
                    "   Станция:   " + sData[number][2] + "\n" +
                    "   НЕТ ДАННЫХ ПО СТАНЦИИ" + "\n";
        } else {
            // есть данные по станции
            for (int i = 0; i <= 6; i++) {
                if (sData[number][3].equals(radioFrequencyChannel[i][0])) {
                    // соответствие каналов найдено
                    index = i;
                    break;
                } else {
                    // соответствие каналов не найдено
                }
            }
            // дополнительная проверка на соответствие данных
            if (index == -1) {
                Toast.makeText(this, "Соответствий в каналах не найдено! " +
                        "Ошибка исходных данных!", Toast.LENGTH_SHORT).show();
            }

            // сформируем данные для отображения
            sTemp = "\n" +
                    "   Дорога:   " + sData[number][0] + "\n" +
                    "   Регион:   " + sData[number][1] + "\n" +
                    "   Станция:  " + sData[number][2] + "\n" +
                    //"   Радиосвязь: " + radioFrequencyChannel[index][1] + "\n" +
                    "\n" +
                    "   Модель:   Режим:" + "\n" +
                    "   РВ-1.1М:  " + radioFrequencyChannel[index][2] + "\n" +
                    "   РВ-1М:    " + radioFrequencyChannel[index][3] + "\n" +
                    "   РВ-1.2МК: " + radioFrequencyChannel[index][4] + "\n" +
                    "   РВС-1:    " + radioFrequencyChannel[index][5] + "\n" +
                    "   РЛСМ-10:  " + radioFrequencyChannel[index][6] + "\n";
        }

        informationForPdfDisplayActivity();
        // выведем результат в поле TextView
        fillTextView(sTemp);
    }

    // подготовим информацию для отображения в textView pdf activity
    public void informationForPdfDisplayActivity() {
        // сформируем информацию для отображения на странице с инструкцией
        choiceFrequency =
                "   Регион:   " + sData[number][1] + "\n" +
                        "   Станция:  " + sData[number][2] + "\n";

        if (!sData[number][3].equals("Нет данных")) {

            if (typeOfRadioStation.equals("notSelected")) {
                // в документации нет выбранной инструкции
                choiceFrequency +=
                        "   РВ-1.1М:  " + radioFrequencyChannel[index][2] + "\n" +
                                "   РВ-1М:    " + radioFrequencyChannel[index][3] + "\n" +
                                "   РВ-1.2МК: " + radioFrequencyChannel[index][4] + "\n" +
                                "   РВС-1:    " + radioFrequencyChannel[index][5] + "\n" +
                                "   РЛСМ-10:  " + radioFrequencyChannel[index][6] + "\n";
            } else if (typeOfRadioStation.equals("РВ-1.1М")) {
                choiceFrequency += "   РВ-1.1М:  " + radioFrequencyChannel[index][2];
            } else if (typeOfRadioStation.equals("РВ-1М")) {
                choiceFrequency += "   РВ-1М:    " + radioFrequencyChannel[index][3];
            } else if (typeOfRadioStation.equals("РВС-1")) {
                choiceFrequency += "   РВС-1:    " + radioFrequencyChannel[index][5];
            } else if (typeOfRadioStation.equals("РЛСМ-10")) {
                choiceFrequency += "   РЛСМ-10:  " + radioFrequencyChannel[index][6];
            }
        } else {
            choiceFrequency =
                    "   Регион:   " + sData[number][1] + "\n" +
                            "   Станция:  " + sData[number][2] + "\n" +
                            "   Нет данных по выбранной станции...";
        }
    }


    // обновим информацию в textView1 в соответствии с выбранными параметрами
    public void fillTextView(String sTemp) {
        TextView textView1 = findViewById(R.id.textView1);
        textView1.setText(sTemp);
        textView1.setVisibility(View.VISIBLE);
        bVisibleTextView = true;
    }

    // сделаем ListView visible = false
    public void listViewInvisible() {
        ListView listView = findViewById(R.id.listView);
        listView.setVisibility(View.INVISIBLE);
    }

    // очистка содержимого в ListView
    public void listViewClear() {
        // получаем экземпляр элемента ListView
        ListView listView = findViewById(R.id.listView);
        // очистим listArray для дальнейшей очистки массива
        stringArrayList.clear();
        // используем адаптер данных
        ArrayAdapter<String> adapter = new
                ArrayAdapter<String>(this, R.layout.my_list_item, stringArrayList);

        listView.setAdapter(adapter);
    }

    // вывод результатов поиска в ListView
    public void searchResultsDisplay() {
        // получаем экземпляр элемента ListView
        ListView listView = findViewById(R.id.listView);
        listView.setVisibility(View.VISIBLE);
        // используем адаптер данных
        ArrayAdapter<String> adapter = new
                ArrayAdapter<String>(this, R.layout.my_list_item, stringArrayList);
        listView.setAdapter(adapter);
        // android.R.layout.simple_list_item_1
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onResume() {
        super.onResume();

        downloadFileData();

        if (sData == null) {
            try {
                readFileData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!bSynchronizationIsCompleted) {
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    dataSynchronizationWithTheServer();
//                }
//            }).start();
            dataSynchronizationWithTheServer();
        }
        Log.d(TAG, "onResume");
    }


    // нарисуем меню
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        return true;
    }

    // обработчик нажатия позиций меню
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // инструкция пользователя
        if (id == R.id.manual) {
            // onLine path
            // Variables.setUrl("https://AleksandrButakov.github.io/MHz/Manual/");
            // local path
            Variables.setUrl("file:///android_asset/manual_menu.html");
            Intent intent = new Intent(this, WebViewActivity.class);
            startActivity(intent);
        }

        // политика конфиденциальности
        if (id == R.id.privacy) {
            // onLine path
            // Variables.setUrl("https://AleksandrButakov.github.io/MHz/PolicyPrivacy/");
            // local path
            Variables.setUrl("file:///android_asset/privacy_policy_menu.html");
            Intent intent = new Intent(this, WebViewActivity.class);
            startActivity(intent);
        }

        // оценить приложение
        if (id == R.id.estimate) {
            //создаём и отображаем текстовое уведомление
            Toast toast = Toast.makeText(this,
                    "Данный раздел станет доступен после публикации приложения в Google Play",
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            /*
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.anbn.pinout"));
            startActivity(intent);
             */
        }

        // о программе
        if (id == R.id.about) {
            // onLine path
            // Variables.setUrl("https://AleksandrButakov.github.io/MHz/About/");
            // local path
            Variables.setUrl("file:///android_asset/about_menu.html");
            Intent intent = new Intent(this, WebViewActivity.class);
            startActivity(intent);
        }
        return true;
    }


    // клик по кнопке получения координат
    public void onClickBtnGetLoc(View view) throws IOException {
        GPSTracker g = new GPSTracker(getApplicationContext()); //создаём трекер
        Location l = g.getLocation(); // получаем координаты
        if (l != null) {
            double lat = l.getLatitude();  // широта
            double lon = l.getLongitude(); // долгота
//            Toast.makeText(getApplicationContext(), "Широта: " + lat +
//                    "\nДолгота: " + lon, Toast.LENGTH_LONG).show(); // вывод в тосте

            /* массив координатами из файла в десятичном виде уже заполнен, координаты
               устройства получены. Необходимо найти ближайшую станцию и отобразить на экране
             */
            number = FindNearestStation.findNearestStation(lat, lon);
            displayTheSelectedPositionListView();

        } else {
//            Toast.makeText(getApplicationContext(), "Координаты не определены, требуется время",
//                    Toast.LENGTH_LONG).show();

            if (bGPSCoordinatesFound) {
                displayToast("Требуется время для поиска координат");
            }
        }
    }


    //        // блок получения координат
//        Button btnGetLoc = findViewById(R.id.btnGetLoc);

//        // запрос разрешение на использовние геопозиции
//        ActivityCompat.requestPermissions(MainActivity.this, new String[]
//                {Manifest.permission.ACCESS_FINE_LOCATION}, 123);
//        btnGetLoc.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                GPSTracker g = new GPSTracker(getApplicationContext()); //создаём трекер
//                Location l = g.getLocation(); // получаем координаты
//                if (l != null) {
//                    double lat = l.getLatitude();  // широта
//                    double lon = l.getLongitude(); // долгота
//                    Toast.makeText(getApplicationContext(), "Широта: " + lat +
//                            "\nДолгота: " + lon, Toast.LENGTH_LONG).show(); // вывод в тосте
//                } else {
//                    Toast.makeText(getApplicationContext(), "Координаты не определены, " +
//                                    "требуется время",
//                            Toast.LENGTH_LONG).show();
//                }
//            }
//        });


    // клик по кнопке для отображения инструкции
    public void onClickBtnNext(View view) throws IOException {
        /* проверяем что выбрана станция и модель оборудования
           отображаем layout с инструкцией pdf
           в случае если станция не выбрана, отображаем инструкцию и пишем что
           необходимо выбрать станцию;
           если выбрана, отображаем и частоту и инструкцию */

        // проверим что инструкция для отображения выбрана
        if (!fileName.equals("")) {
            if (bVisibleTextView) {
                // подготовим переменную choiceFrequency for display in activity
                informationForPdfDisplayActivity();

                // оборудование выбрано, станция выбрана, отображаем инструкцию
                // переходим к ActivityTwo
                Intent intent = new Intent(this, PdfReaderActivity.class);
                startActivity(intent);
            } else {
                // станция не выбрана, выводим Toast
                displayToast("Выберите станцию...");
            }
        } else {
            // оборудование не выбрано, выводим Toast
            displayToast("Выберите оборудование...");
        }
    }


    private void downloadFileData() {
        // проверим что локальный файл mhz_data.txt существует
        File fileLocalData = new File(getExternalFilesDir(null), FILE_PATH_LOCAL_DATA);
        // проверим что файл существует
        if (!fileLocalData.exists()) {
            /* проверка наличия подключения к интернету и в случае отсутствия
               интернета прерываем программу */
            if (!isOnline()) {
                displayToast("Отсутствует подключение к сети интернет. Данные не загружены.");
                return;
            }
            // загрузка файлов mhz_data.csv
            downloadFile(FILE_PATH_YANDEX_DISK_DATA, FILE_PATH_LOCAL_DATA);
            // переменная необходима для исключения повторной загрузки файла
            bFirstRun = true;
            bSynchronizationIsCompleted = true;
        }
    }


    // основной алгоритм проверки наличия файл данных и его чтения
    private void readFileData() throws IOException {
        // проверим что локальный файл mhz_data.txt существует
        File fileLocalData = new File(getExternalFilesDir(null), FILE_PATH_LOCAL_DATA);

        // определим количество строк в файле
        countRows = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(fileLocalData))) {
            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println(line);
                countRows++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // зададим размерность массива в соответствии с размером файла
        sData = new String[countRows][12];
        dGeographicCoordinates = new double[countRows][2];

        // открываем файл
        BufferedReader reader = new BufferedReader(new FileReader(fileLocalData));
        // считываем построчно
        String line = null;
        Scanner scanner = null;
        int id = 0;
        int index = 0;

        // заполняем массив данными из файла mhz_data.csv
        String data;
        String version;
        while ((line = reader.readLine()) != null) {
            //Frequency frequency = new Frequency();
            scanner = new Scanner(line);
            scanner.useDelimiter(";");
            while (scanner.hasNext()) {
                // заполнение данными массива
                data = scanner.next();
                if (index == 0)
                    sData[id][index] = data;
                else if (index == 1)
                    sData[id][index] = data;
                else if (index == 2)
                    sData[id][index] = data;
                else if (index == 3)
                    sData[id][index] = data;
                else if (index == 4) ;
                    //
                else if (index == 5) ;
                    //
                else if (index == 6)
                    // широта градусы
                    sData[id][index] = data;
                else if (index == 7)
                    // широта минуты
                    sData[id][index] = data;
                else if (index == 8)
                    // широта секунды
                    sData[id][index] = data;
                else if (index == 9)
                    // долгота градусы
                    sData[id][index] = data;
                else if (index == 10)
                    // долгота минуты
                    sData[id][index] = data;
                else if (index == 11)
                    // долгота секунды
                    sData[id][index] = data;
                // System.out.println("Некорректные данные: " + data);
                index++;
            }
            id++;
            index = 0;

        }
        // закрываем reader
        reader.close();

        // заполним массив данными о широте и долготе в десятичном формате
        for (int i = 2; i < countRows; i++) {
            // широта
            dGeographicCoordinates[i][0] = Double.parseDouble(sData[i][6]);
            dGeographicCoordinates[i][0] += (Double.parseDouble(sData[i][7])) / 60;
            dGeographicCoordinates[i][0] += (Double.parseDouble(sData[i][8])) / 3600;
            // долгота
            dGeographicCoordinates[i][1] = Double.parseDouble(sData[i][9]);
            dGeographicCoordinates[i][1] += (Double.parseDouble(sData[i][10])) / 60;
            dGeographicCoordinates[i][1] += (Double.parseDouble(sData[i][11])) / 3600;
        }
    }

    // синхронизация данных с сервером
    public void dataSynchronizationWithTheServer() {
        // проверим что локальный файл mhz_data.txt существует
        File fileLocalData = new File(getExternalFilesDir(null), FILE_PATH_LOCAL_DATA);
        File fileLocalDataTemp = new File(getExternalFilesDir(null), FILE_PATH_LOCAL_DATA_TEMP);
        // удалим файл
        deletingFile(fileLocalDataTemp);
        // проверим что файла не существует
        if (!fileLocalDataTemp.exists()) {
            // проверяем наличие сети Интернет. При наличии загружаем файл с сервера
            if (isOnline()) {
                // загрузка файлов mhz_data.csv /sdcard/Android/data/ru.anbn.mhz/files/temp.csv
                downloadFile(FILE_PATH_YANDEX_DISK_DATA, FILE_PATH_LOCAL_DATA_TEMP);
                // если файл существует то выполняем блок
                if (fileLocalDataTemp.exists()) {
                    // удаляем основной рабочий файл
                    fileLocalData.delete();
                    // переименовываем вновь загруженный файл с именем temp.csv in mhz_data.csv
                    fileLocalDataTemp.renameTo(fileLocalData);
                }
            }
        }
        // вспомогательная переменнная говорит нам что синхронизация выполнена
        bSynchronizationIsCompleted = true;
    }

    // проверка наличия подключения к интернету
    protected boolean isOnline() {
        String cs = Context.CONNECTIVITY_SERVICE;
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(cs);
        if (cm.getActiveNetworkInfo() == null) {
            return false;
        } else {
            return true;
        }
    }

    // загрузка файла из Yandex Disk на смартфон
    private void downloadFile(String pathServerFile, String pathLocalFile) {
        File file = new File(getExternalFilesDir(null), pathLocalFile);

        // если такой файл уже существует то перед загрузкой новой версии удалим его
        deletingFile(file);

        // загрузка файла
        DownloadManager.Request request_version = null;
        request_version = new DownloadManager.Request(Uri.parse(pathServerFile))
                .setTitle(pathLocalFile)
                //.setDescription("Downloading")
                //.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setRequiresCharging(false)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadId = downloadManager.enqueue(request_version);

        // ожидание загрузки файла
        countSleep = timerSeconds;
        while (!file.exists() && countSleep > 0) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            countSleep--;
        }
    }

    // метод удаления файла
    public void deletingFile(File file) {
        // если такой файл уже существует то перед загрузкой новой версии удалим его
        if (file.exists()) {
            file.delete();
        }

        // ожидаем уделение файла
        countSleep = timerSeconds;
        while (file.exists() && countSleep > 0) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            countSleep--;
        }
    }

}