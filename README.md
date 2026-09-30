# Bài tập JWT với Spring Boot

Triển khai ví dụ từ slide 15–34 của bài giảng: đăng ký → đăng nhập nhận JWT → gọi API người dùng → hiển thị hồ sơ bằng AJAX.

## Công nghệ

- Java 17 trở lên; Spring Boot **4.1.1** và Spring Security do Boot quản lý.
- Spring MVC, Spring Data JPA, Validation, Thymeleaf.
- JJWT **0.12.6**, JWT ký HS256, mật khẩu BCrypt.
- H2 dạng tệp, không cần cài MySQL; jQuery **3.7.1** được lưu sẵn trong dự án.

## Chạy ứng dụng

Mở PowerShell tại thư mục dự án:

```powershell
.\mvnw.cmd spring-boot:run
```

Lần đầu cần Internet để Maven tải dependency. Mở **http://localhost:8005/login**. Trong IntelliJ cũng có thể chạy `nguyen.vn.JwtSpringbootApplication` trực tiếp.

Nếu Maven báo không tìm thấy Java, đặt `JAVA_HOME` trỏ đến thư mục JDK đã cài. Nếu cổng 8005 đang bận, dừng phiên ứng dụng cũ trước khi chạy lại.

Ứng dụng không tự tạo tài khoản mặc định. Tạo tài khoản bằng API bên dưới rồi đăng nhập trên giao diện. Nếu đã có tài khoản từ lần chạy trước, đăng nhập lại bằng tài khoản đó.

## Thử API bằng Postman

Đặt base URL là `http://localhost:8005`. Hai request POST dùng **Body → raw → JSON**, header `Content-Type: application/json`.

### 1. Đăng ký: `POST /auth/signup`

```json
{
  "fullName": "Nguyễn Văn An",
  "email": "student@example.com",
  "password": "123456"
}
```

Trả HTTP 200 với `id`, `fullName`, `email`, `images`, `createdAt`, `updatedAt`. Email là duy nhất và được chuyển sang chữ thường. Họ tên tối đa 50 ký tự, email tối đa 100 ký tự, mật khẩu tối thiểu 6 ký tự và tối đa 72 byte UTF-8 (giới hạn BCrypt). Không trả mật khẩu hoặc hash.

### 2. Đăng nhập: `POST /auth/login`

```json
{
  "email": "student@example.com",
  "password": "123456"
}
```

Trả HTTP 200:

```json
{
  "token": "<JWT được máy chủ tạo>",
  "expiresIn": 3600000
}
```

`expiresIn` tính bằng **mili giây**; mặc định một giờ. JWT chứa subject là email, thời điểm phát hành và thời điểm hết hạn.

### 3. Gọi API được bảo vệ

Trong Postman, chọn **Authorization → Bearer Token**, dán giá trị `token` vừa nhận:

| Request | Kết quả |
| --- | --- |
| `GET /users/me` | Hồ sơ tài khoản đang đăng nhập |
| `GET /users` hoặc `GET /users/` | Danh sách người dùng |

Header tương ứng: `Authorization: Bearer <token>`. Theo bài tập, mọi tài khoản đã xác thực đều xem được danh sách; chưa có vai trò admin/user.

### Thử nhanh bằng PowerShell

```powershell
$baseUrl = 'http://localhost:8005'
$register = @{ fullName = 'Nguyễn Văn An'; email = 'student@example.com'; password = '123456' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/signup" -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($register))

$credentials = @{ email = 'student@example.com'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" -ContentType 'application/json' -Body $credentials
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Uri "$baseUrl/users/me" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/users" -Headers $headers
```

Chạy request đăng ký một lần; đăng ký lại cùng email trả 409.

## Giao diện AJAX

1. Mở `/login`, nhập email và mật khẩu đã đăng ký.
2. `mainjs.js` gọi `/auth/login`, lưu token tại khóa `jwt-springboot.token` trong `localStorage` rồi chuyển sang `/user/profile`.
3. Trang hồ sơ gọi `/users/me` với Bearer token, hiển thị họ tên, email, ảnh mặc định, mã tài khoản và ngày đăng ký.
4. Tải lại trang vẫn giữ đăng nhập nếu token còn hợp lệ. Token sai/hết hạn hoặc tài khoản không còn khả dụng sẽ bị xóa và chuyển về trang đăng nhập.
5. Đăng xuất chỉ xóa token của ứng dụng. API vẫn stateless; token đã sao chép ở nơi khác còn hiệu lực đến khi hết hạn. Bài tập không có refresh token hoặc danh sách thu hồi token.

Các trang HTML được phép truy cập công khai để tải giao diện; dữ liệu `/users/**` luôn được kiểm tra tại máy chủ. Mật khẩu/tên người dùng không được chèn vào HTML bằng `.html()`; dữ liệu hồ sơ dùng `.text()`.

## Cấu hình và dữ liệu

`src/main/resources/application.properties`:

| Thuộc tính | Mặc định |
| --- | --- |
| `server.port` | `8005` |
| `spring.datasource.url` | `jdbc:h2:file:./data/jwt_springboot` |
| `spring.jpa.hibernate.ddl-auto` | `update` |
| `security.jwt.expiration-time` | `3600000` |
| `security.jwt.secret-key` | Biến môi trường `JWT_SECRET`, hoặc khóa tạm nếu chưa đặt |

H2 lưu tài khoản trong thư mục `data/` bên dưới **thư mục làm việc khi chạy ứng dụng**. Chạy từ thư mục gốc dự án để dùng cùng dữ liệu giữa Maven, JAR và IntelliJ. Thư mục dữ liệu được bỏ qua trong `.gitignore`; H2 console không bật. Không chạy hai phiên ứng dụng đồng thời trên cùng tệp H2.

Nếu chưa đặt `JWT_SECRET`, ứng dụng tự sinh khóa ngẫu nhiên cho lần chạy đó. Khởi động lại vẫn giữ tài khoản, nhưng phải đăng nhập lại để lấy token mới.

Để giữ khóa giữa các lần chạy, tạo chuỗi Base64 từ ít nhất 32 byte ngẫu nhiên. Ví dụ trong PowerShell, chạy một lần trong phiên terminal hiện tại:

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtKeyBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
.\mvnw.cmd spring-boot:run
```

Biến trên tồn tại trong phiên terminal hiện tại. Khi chạy từ IntelliJ, đặt cùng giá trị `JWT_SECRET` trong Run Configuration nếu cần giữ token. Không commit khóa vào mã nguồn.

## Xử lý ngoại lệ theo slide (Bước 10)

Xử lý ngoại lệ tại `GlobalExceptionHandler` theo đúng slide 16 & 17:

| HTTP Status | Ngoại lệ ném ra | Mô tả |
| --- | --- | --- |
| 401 | `BadCredentialsException` | The username or password is incorrect |
| 403 | `AccountStatusException` | The account is locked |
| 403 | `AccessDeniedException` | You are not authorized to access this resource |
| 403 | `SignatureException` | The JWT signature is invalid |
| 403 | `ExpiredJwtException` | The JWT token has expired |
| 500 | Ngoại lệ khác / Token không đúng định dạng | Unknown internal server error. |

## Cấu trúc dự án theo slide

- `entity`: [User.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/entity/User.java) (Bước 2) - Entity `users` triển khai `UserDetails`, trả về dữ liệu người dùng (bao gồm password hash theo slide 14).
- `model`: [LoginResponse.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/model/LoginResponse.java), [LoginUserModel.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/model/LoginUserModel.java), [RegisterUserModel.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/model/RegisterUserModel.java) (Bước 3).
- `repository`: [UserRepository.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/repository/UserRepository.java) (Bước 4) - Tìm user theo email.
- `service`: [UserService.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/service/UserService.java), [AuthenticationService.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/service/AuthenticationService.java), [JwtService.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/service/JwtService.java) (Bước 4).
- `config`: [ApplicationConfiguration.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/config/ApplicationConfiguration.java) (Bước 5), [SecurityConfiguration.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/config/SecurityConfiguration.java) (Bước 7).
- `filter`: [JwtAuthenticationFilter.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/filter/JwtAuthenticationFilter.java) (Bước 6).
- `controller`: [AuthenticationController.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/controller/AuthenticationController.java), [UserController.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/controller/UserController.java) (Bước 8), [AuthController.java](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/java/nguyen/vn/controller/AuthController.java) (Bước 10).
- `templates`: [login.html](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/resources/templates/login.html), [profile.html](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/resources/templates/profile.html) (Bước 10).
- `static`: [mainjs.js](file:///c:/Users/nhatn/IdeaProjects/JWT-springboot/src/main/resources/static/js/mainjs.js) (Bước 10).

## Kiểm thử và đóng gói

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/JWT-springboot-0.0.1-SNAPSHOT.jar
```
