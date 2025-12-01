module com.example.minicompilernew {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.minicompilernew to javafx.fxml;
    exports com.example.minicompilernew;
}