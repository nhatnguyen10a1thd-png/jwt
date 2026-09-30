$(document).ready(function() {

    // ===== Trang Profile: Hiển thị thông tin người dùng đã đăng nhập =====
    if ($('#profileName').length) {
        var token = localStorage.getItem('jwt-springboot.token');
        if (!token) {
            window.location.href = '/login';
            return;
        }
        $.ajax({
            type: 'GET',
            url: '/users/me',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            beforeSend: function(xhr) {
                xhr.setRequestHeader('Authorization', 'Bearer ' + token);
            },
            success: function(data) {
                // Profile header
                $('#profileName').text(data.fullName);
                document.getElementById('images').src = data.images || '/images/profile.png';

                // Stats cards
                $('#userId').text('#' + data.id);
                $('#userEmail').text(data.email);
                if (data.createdAt) {
                    var d = new Date(data.createdAt);
                    var formatted = d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
                    $('#userCreated').text(formatted);
                    $('#detailCreated').text(d.toLocaleString('vi-VN'));
                }

                // Detail card
                $('#detailName').text(data.fullName);
                $('#detailEmail').text(data.email);
                $('#detailId').text('#' + data.id);
                if (data.updatedAt) {
                    var u = new Date(data.updatedAt);
                    $('#detailUpdated').text(u.toLocaleString('vi-VN'));
                }
            },
            error: function(e) {
                // Token không hợp lệ hoặc hết hạn -> xóa và về trang login
                localStorage.removeItem('jwt-springboot.token');
                window.location.href = '/login';
            }
        });
    }

    // ===== Nút Đăng xuất =====
    $('#logout').click(function() {
        localStorage.removeItem('jwt-springboot.token');
        window.location.href = '/login';
    });

    // ===== Trang Login: Xử lý đăng nhập =====
    $('#login').click(function() {
        var email = document.getElementById('email').value;
        var password = document.getElementById('password').value;
        var $alert = $('#alertMessage');
        var $btnText = $('#loginText');
        var $spinner = $('#loginSpinner');

        // Validation
        if (!email || !password) {
            $alert.removeClass('success').addClass('error').text('Vui lòng nhập đầy đủ email và mật khẩu.').show();
            return;
        }

        // UI loading state
        $alert.hide();
        $btnText.text('Đang xử lý...');
        $spinner.show();
        $('#login').prop('disabled', true);

        var basicInfo = JSON.stringify({ email: email, password: password });

        $.ajax({
            type: 'POST',
            url: '/auth/login',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: basicInfo,
            success: function(data) {
                localStorage.setItem('jwt-springboot.token', data.token);
                $alert.removeClass('error').addClass('success').text('Đăng nhập thành công! Đang chuyển hướng...').show();
                setTimeout(function() {
                    window.location.href = '/user/profile';
                }, 600);
            },
            error: function(e) {
                $btnText.text('Đăng nhập');
                $spinner.hide();
                $('#login').prop('disabled', false);

                var msg = 'Email hoặc mật khẩu không chính xác.';
                if (e.responseJSON && e.responseJSON.detail) {
                    msg = e.responseJSON.detail;
                }
                $alert.removeClass('success').addClass('error').text(msg).show();
            }
        });
    });

    // ===== Submit form bằng Enter =====
    $('#loginForm').on('submit', function(event) {
        event.preventDefault();
        $('#login').click();
    });

    // ===== Auto-redirect nếu đã có token trên trang login =====
    if ($('#loginForm').length) {
        var existingToken = localStorage.getItem('jwt-springboot.token');
        if (existingToken) {
            // Kiểm tra token còn hiệu lực không
            $.ajax({
                type: 'GET',
                url: '/users/me',
                beforeSend: function(xhr) {
                    xhr.setRequestHeader('Authorization', 'Bearer ' + existingToken);
                },
                success: function() {
                    window.location.href = '/user/profile';
                },
                error: function() {
                    localStorage.removeItem('jwt-springboot.token');
                }
            });
        }
    }
});
